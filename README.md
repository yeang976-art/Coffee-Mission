# Solution Architecture

## 구현 현황

현재는 기본 API와 주문 트랜잭션을 구현한 단계입니다. 외부 플랫폼 전송까지 완료된 상태는 아닙니다.

| 항목 | 현재 상태 |
|---|---|
| 판매 중인 커피 메뉴 조회 | 구현 완료 |
| 포인트 조회 및 충전 | 구현 완료 |
| 주문 및 포인트 결제 | 구현 완료 |
| 주문·포인트 이력·Outbox의 원자적 저장 | 구현 완료, H2 롤백 테스트 통과 |
| 최근 7일 인기 메뉴 TOP 3 | 구현 완료 |
| 지갑 비관적 락 | 충전과 주문에 적용, MySQL 동시 요청 검증은 남아 있습니다. |
| Outbox 상태 변경 | 구현 완료, 단위 테스트 통과 |
| 외부 플랫폼 전송·재시도 작업자 | 미구현 |
| 중단된 PROCESSING 복구·중복 전송 처리 | 미구현 |

사용자와 지갑, 메뉴는 사전에 준비된 데이터를 사용합니다. 회원가입·로그인·관리자 메뉴 API는 추가하지 않습니다.
DB에 저장하는 시각은 UTC 기준의 LocalDateTime이며 주문 API에서는 UTC Instant로 변환해 반환합니다.
생성 시각만 필요한 엔티티와 갱신 시각만 필요한 지갑은 ERD에 맞는 시각 필드를 직접 매핑합니다.

## ERD

<img width="1300" height="724" alt="image" src="https://github.com/user-attachments/assets/421bf7b9-c644-4bbe-8eca-8f665badc0a0" />

## Enum 설계

### 포인트 이력 유형 — PointHistoryType

포인트 변경 사유를 구분합니다.

| 값 | 의미 | amount | order_id |
|---|---|---|---|
| CHARGE | 포인트 충전 | 충전 금액, 양수 | NULL |
| SPEND | 주문 결제에 포인트 사용 | 사용 금액, 양수 | 결제한 주문 ID |

- `amount`는 충전과 사용 모두 양수로 저장하며, 변경 사유는 `type`으로 구분합니다.
- `balance_after`에는 해당 작업 직후의 잔액을 저장합니다.
- 실패하여 롤백된 작업은 이력을 남기지 않습니다.
- 환불은 현재 과제 범위에 포함하지 않습니다.

### 외부 전송 상태 — OutboxStatus

결제된 주문 데이터의 외부 플랫폼 전송 상태를 관리합니다.
주문 결제의 성공 여부와 외부 전송의 성공 여부는 별도로 관리합니다.

| 값 | 의미 |
|---|---|
| PENDING | 최초 전송 대기 |
| PROCESSING | 전송 작업 진행 중 |
| SENT | 외부 플랫폼 전송 성공 |
| FAILED | 전송 시도 실패, 재시도 가능 |

상태 전이는 다음과 같습니다.

- 최초 전송: `PENDING → PROCESSING → SENT 또는 FAILED`
- 재시도: `FAILED → PROCESSING → SENT 또는 FAILED`

관련 컬럼은 다음 기준으로 사용합니다.

| 컬럼 | 의미 |
|---|---|
| attempt_count | 실제 전송 시도 횟수입니다. 최초 값은 0입니다. |
| next_attempt_at | 실패 후 다음 전송을 시도할 수 있는 시각입니다. |
| processing_started_at | 현재 전송 작업을 시작한 시각입니다. |
| last_error | 최근 전송 실패 원인입니다. |
| sent_at | 전송 성공 시각입니다. 성공 전에는 NULL입니다. |

외부 전송 실패는 이미 완료된 주문과 포인트 결제를 롤백시키지 않습니다.
재시도 과정에서 중복 전송이 발생할 수 있으므로 Outbox ID를 이벤트 식별값으로 전달합니다.

### Enum 저장 방식

`@Enumerated(EnumType.STRING)`을 사용하여 Enum 이름을 DB에 저장합니다.
숫자 순서에 의존하지 않아 Enum 선언 순서가 변경되어도 기존 데이터의 의미가 유지됩니다.

## API 명세서

### API 설계 기준

- 금액과 포인트는 `long` 타입의 정수로 처리하며, **1원 = 1P**입니다.
- 주문 요청에는 메뉴 가격을 받지 않습니다. 서버가 `coffee_menus.price`를 조회하고, 결제한 금액을 `coffee_orders.paid_price`에 저장합니다.
- `coffee_orders`에는 결제에 성공한 주문만 저장합니다. 주문 한 건은 메뉴 한 개에 해당합니다.
- 현재 과제 범위에는 로그인 API가 없습니다. 사용자별 요청은 `userId`로 사용자를 식별하며, 사용자와 포인트 지갑은 사전에 준비되어 있다고 가정합니다.
- 응답 시각은 UTC 기준 ISO 8601 형식으로 표현합니다.

### 공통 응답 형식

성공:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

실패:

```json
{
  "success": false,
  "code": "INSUFFICIENT_POINT",
  "message": "포인트가 부족합니다."
}
```



### 1. 커피 메뉴 목록 조회

| 항목 | 내용 |
|---|---|
| Domain | CoffeeMenu |
| Method | `GET` |
| Endpoint | `/api/coffee-menus` |
| 설명 | 현재 판매 중인 커피 메뉴의 ID, 이름, 가격을 조회한다. |
| 인증 필요 여부 | 아니요 |
| Path Variable | 없음 |
| Query Parameter | 없음 |
| Request Body | 없음 |
| Response Body | `menus[]`: `menuId`(`long`), `name`(`string`), `price`(`long`) |
| 정상 Status Code | `200 OK` |
| 주요 실패 Status Code | `500 Internal Server Error` |
| 주요 ErrorCode | `INTERNAL_SERVER_ERROR` |

응답 예시:

```json
{
  "success": true,
  "message": "커피 메뉴를 조회했습니다.",
  "data": {
    "menus": [
      { "menuId": 1, "name": "아메리카노", "price": 4500 },
      { "menuId": 2, "name": "카페라떼", "price": 5000 }
    ]
  }
}
```

`active = true`인 메뉴만 반환합니다. 판매 중인 메뉴가 없으면 `menus`는 빈 배열입니다.

### 2. 포인트 충전

| 항목 | 내용 |
|---|---|
| Domain | Point |
| Method | `POST` |
| Endpoint | `/api/points/charge` |
| 설명 | 사용자 지갑에 포인트를 충전하고 충전 이력을 저장한다. |
| 인증 필요 여부 | 아니요 |
| Path Variable | 없음 |
| Query Parameter | 없음 |
| Request Body | `userId`(`long`, 필수), `amount`(`long`, 필수·양수) |
| Response Body | `userId`(`long`), `chargedAmount`(`long`), `balance`(`long`) |
| 정상 Status Code | `200 OK` |
| 주요 실패 Status Code | `400 Bad Request`, `404 Not Found`, `409 Conflict` |
| 주요 ErrorCode | `INVALID_REQUEST`, `INVALID_POINT_AMOUNT`, `USER_NOT_FOUND`, `WALLET_NOT_FOUND`, `POINT_BALANCE_OVERFLOW` |

요청 예시:

```json
{
  "userId": 1,
  "amount": 10000
}
```

응답 예시:

```json
{
  "success": true,
  "message": "포인트를 충전했습니다.",
  "data": {
    "userId": 1,
    "chargedAmount": 10000,
    "balance": 10000
  }
}
```

충전 금액이 0 이하이거나 충전 후 잔액이 `long` 범위를 초과하면 충전하지 않습니다. 지갑 잔액 변경과 `point_histories`의 `CHARGE` 이력 저장은 하나의 트랜잭션으로 처리합니다. 충전 이력의 `order_id`는 `NULL`입니다.

### 3. 현재 포인트 조회

| 항목 | 내용 |
|---|---|
| Domain | Point |
| Method | `GET` |
| Endpoint | `/api/points` |
| 설명 | 사용자의 현재 포인트 잔액을 조회한다. |
| 인증 필요 여부 | 아니요 |
| Path Variable | 없음 |
| Query Parameter | `userId`(`long`, 필수) |
| Request Body | 없음 |
| Response Body | `userId`(`long`), `balance`(`long`) |
| 정상 Status Code | `200 OK` |
| 주요 실패 Status Code | `400 Bad Request`, `404 Not Found` |
| 주요 ErrorCode | `INVALID_REQUEST`, `USER_NOT_FOUND`, `WALLET_NOT_FOUND` |

요청 예시: `GET /api/points?userId=1`

응답 예시:

```json
{
  "success": true,
  "message": "포인트를 조회했습니다.",
  "data": {
    "userId": 1,
    "balance": 10000
  }
}
```

### 4. 커피 주문 및 포인트 결제

| 항목 | 내용 |
|---|---|
| Domain | Order |
| Method | `POST` |
| Endpoint | `/api/orders` |
| 설명 | 메뉴 한 개를 주문하고 사용자 포인트로 결제한다. |
| 인증 필요 여부 | 아니요 |
| Path Variable | 없음 |
| Query Parameter | 없음 |
| Request Body | `userId`(`long`, 필수), `menuId`(`long`, 필수). 가격은 받지 않는다. |
| Response Body | `orderId`(`long`), `userId`(`long`), `menuId`(`long`), `paidPrice`(`long`), `remainingBalance`(`long`), `orderedAt`(ISO 8601 시각) |
| 정상 Status Code | `201 Created` |
| 주요 실패 Status Code | `400 Bad Request`, `404 Not Found`, `409 Conflict` |
| 주요 ErrorCode | `INVALID_REQUEST`, `USER_NOT_FOUND`, `WALLET_NOT_FOUND`, `MENU_NOT_FOUND`, `MENU_NOT_AVAILABLE`, `INSUFFICIENT_POINT` |

요청 예시:

```json
{
  "userId": 1,
  "menuId": 1
}
```

응답 예시:

```json
{
  "success": true,
  "message": "주문과 결제가 완료되었습니다.",
  "data": {
    "orderId": 100,
    "userId": 1,
    "menuId": 1,
    "paidPrice": 4500,
    "remainingBalance": 5500,
    "orderedAt": "2026-10-01T06:00:00Z"
  }
}
```

서버는 메뉴의 존재 여부와 판매 가능 여부를 확인한 뒤 현재 가격으로 결제합니다. 잔액이 부족하면 주문 생성과 포인트 차감이 모두 발생하지 않습니다.

잔액 차감, `coffee_orders` 저장, `point_histories`의 `SPEND` 이력 저장, `order_outbox` 저장은 **하나의 트랜잭션**으로 처리합니다. `SPEND` 이력의 `order_id`는 생성된 주문 ID를 가리킵니다.

### 5. 최근 7일 인기 메뉴 TOP 3 조회

| 항목 | 내용 |
|---|---|
| Domain | CoffeeMenu |
| Method | `GET` |
| Endpoint | `/api/coffee-menus/popular` |
| 설명 | 최근 7일간 주문 횟수가 많은 메뉴를 최대 3개 조회한다. |
| 인증 필요 여부 | 아니요 |
| Path Variable | 없음 |
| Query Parameter | 없음 |
| Request Body | 없음 |
| Response Body | `menus[]`: `menuId`(`long`), `name`(`string`), `price`(`long`), `active`(`boolean`), `orderCount`(`long`) |
| 정상 Status Code | `200 OK` |
| 주요 실패 Status Code | `500 Internal Server Error` |
| 주요 ErrorCode | `INTERNAL_SERVER_ERROR` |

응답 예시:

```json
{
  "success": true,
  "message": "인기 메뉴를 조회했습니다.",
  "data": {
    "menus": [
      {
        "menuId": 1,
        "name": "아메리카노",
        "price": 4500,
        "active": true,
        "orderCount": 25
      }
    ]
  }
}
```

조회 시각으로부터 직전 7일의 `coffee_orders`를 `menu_id`별로 집계합니다. 주문 횟수 내림차순으로 정렬하고, 횟수가 같으면 `menuId` 오름차순으로 정렬합니다. 해당 기간의 주문이 없으면 빈 배열을 반환합니다. 현재 판매가 중지된 메뉴도 실제 주문 기록이 있으면 집계에 포함합니다.

### ErrorCode

| ErrorCode | Status Code | 발생 조건 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 필수 필드가 없거나 ID 형식이 잘못된 경우 |
| `INVALID_POINT_AMOUNT` | 400 | 충전 금액이 0 이하인 경우 |
| `USER_NOT_FOUND` | 404 | 사용자가 존재하지 않는 경우 |
| `WALLET_NOT_FOUND` | 404 | 사용자의 포인트 지갑이 없는 경우 |
| `MENU_NOT_FOUND` | 404 | 주문할 메뉴가 존재하지 않는 경우 |
| `MENU_NOT_AVAILABLE` | 409 | 판매 중지된 메뉴를 주문한 경우 |
| `INSUFFICIENT_POINT` | 409 | 주문 금액보다 잔액이 적은 경우 |
| `POINT_BALANCE_OVERFLOW` | 409 | 충전 후 잔액이 `long` 범위를 초과하는 경우 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 서버 오류가 발생한 경우 |

`ORDER_NOT_FOUND`는 주문 조회 API가 없는 현재 범위에서 사용하지 않습니다. `INVALID_REQUEST`는 요청 형식 오류를, `POINT_BALANCE_OVERFLOW`는 금액 계산 범위 초과를 구분하기 위해 추가했습니다.

### 요구사항과 API 매핑

| 과제 요구사항 | 처리 방식 |
|---|---|
| 커피 메뉴 목록 조회 | `GET /api/coffee-menus` |
| 포인트 충전 | `POST /api/points/charge` |
| 현재 포인트 조회 | `GET /api/points` |
| 커피 주문 및 결제 | `POST /api/orders` |
| 최근 7일 인기 메뉴 TOP 3 | `GET /api/coffee-menus/popular` |
| 결제 주문의 외부 전송 | 주문과 함께 Outbox 기록 저장 완료. 외부 전송 작업자는 구현 예정입니다. |
| 동시 주문 시 잔액 정합성 | 주문과 충전 시 동일한 사용자 지갑 행을 비관적 락으로 조회 |

공개 **필수 API는 위 5개**이며, 현재 선택 API는 없습니다. 외부 전송은 주문 커밋 후 시도하고 실패 시 Outbox를 바탕으로 재시도하도록 설계했습니다. 현재는 Outbox 저장과 상태 변경까지 구현했으며, 실제 전송 작업자는 구현 예정입니다.

## 설계의 의도

이 과제의 핵심은 커피 주문 기능 자체보다 **포인트 결제 과정에서 데이터의 정합성을 지키는 것**입니다. 사용자가 동시에 여러 주문을 보내더라도 잔액이 음수가 되지 않아야 하며, 결제가 실패했다면 주문과 포인트 사용 이력이 남지 않아야 합니다.

이를 위해 사용자별 현재 잔액은 `point_wallets`에, 충전·사용 기록은 `point_histories`에 분리합니다. `point_wallets.user_id`에 UNIQUE 제약을 두어 사용자당 지갑을 하나만 허용합니다. 주문 금액은 서버가 메뉴 가격을 조회해 결정하고, `coffee_orders.paid_price`에 결제 당시 가격을 저장합니다. 이후 메뉴 가격이 변경되어도 기존 주문의 결제금액은 유지됩니다.

주문 한 건에 메뉴 한 개를 주문하는 과제 범위에 맞춰 `order_items`를 만들지 않습니다. 고정 캐릭터 한 명은 화면에서 서비스를 안내하는 요소로 사용하며, 캐릭터·채팅 관련 테이블은 과제 ERD에 포함하지 않습니다. 이 커피 주문 도메인은 VisualChat 개인 프로젝트의 최종 도메인과 별도로 관리합니다.

## 선택한 문제해결 전략 및 분석 내용

| 검토한 문제 | 분석한 내용 | 선택한 전략 |
|---|---|---|
| 동시 주문으로 인한 잔액 초과 사용 | 두 요청이 같은 잔액을 읽고 각각 차감하면 잔액 부족 주문이 성공할 수 있습니다. 충전과 주문이 동시에 실행될 때도 갱신이 누락될 수 있습니다. | 잔액을 변경하는 충전·주문 모두 같은 `point_wallets` 행에 비관적 락을 적용합니다. 락을 얻은 뒤 잔액을 확인하고 변경합니다. |
| 결제 중 일부 데이터만 저장되는 문제 | 잔액 차감, 주문, 사용 이력이 따로 저장되면 중간 실패 시 기록이 어긋납니다. | 잔액 차감, `coffee_orders`, `point_histories`, `order_outbox` 저장을 하나의 주문 트랜잭션으로 처리합니다. |
| 클라이언트가 잘못된 가격을 보내는 문제 | 요청 가격을 신뢰하면 실제 메뉴 가격과 다른 금액으로 결제될 수 있습니다. | 주문 요청에는 `menuId`만 받고 서버에서 가격을 조회합니다. 결제 가격은 주문에 별도로 저장합니다. |
| 외부 데이터 수집 플랫폼 장애 | 외부 전송을 주문 트랜잭션 안에서 수행하면 응답 지연이나 외부 장애가 결제에 영향을 줄 수 있습니다. | 주문과 함께 Outbox 기록을 저장하고, 커밋 후 전송합니다. 실패 기록은 재시도합니다. |
| 인기 메뉴 집계의 정확성 | 별도 집계값을 주문마다 갱신하면 주문 기록과 집계값의 정합성을 추가로 관리해야 합니다. | 실제 결제된 `coffee_orders`를 기준으로 최근 7일 주문 횟수를 `menu_id`별로 집계합니다. |

주문 트랜잭션에서는 **메뉴 확인 → 지갑 잠금 → 잔액 확인 → 포인트 차감 → 주문·사용 이력·Outbox 저장** 순서로 처리합니다. 잔액이 부족하거나 저장에 실패하면 전체 작업을 롤백합니다. 판매 중지된 메뉴도 결제 전에 거절합니다.

인기 메뉴는 조회 시각으로부터 직전 7일의 주문을 집계해 상위 3개를 반환합니다. 주문 횟수가 같으면 `menuId` 오름차순으로 정렬해 결과가 일정하도록 합니다.

## 기술적 선택 이유

| 선택 | 이유 |
|---|---|
| `long`으로 금액 저장 | 원/P 단위의 정수를 저장해 소수 계산을 피합니다. 충전 시에는 0보다 큰 금액인지, 계산 결과가 `long` 범위를 넘지 않는지 확인합니다. |
| 관계형 DB의 PK·FK·UNIQUE 제약 | 사용자당 지갑 하나, 주문당 Outbox 기록 하나, 주문당 포인트 사용 이력 하나라는 관계를 데이터베이스에서도 보호합니다. |
| Spring/JPA의 비관적 락 | 여러 서버 인스턴스가 같은 DB를 사용해도 사용자 지갑 행을 기준으로 잔액 변경을 순서대로 처리할 수 있습니다. 이번 범위에서는 Redis나 분산락을 사용하지 않습니다. |
| Service 계층의 트랜잭션 | 주문을 완료하는 데 필요한 DB 변경을 하나의 작업 단위로 묶습니다. 외부 HTTP 전송은 이 트랜잭션에 포함하지 않습니다. |
| Outbox 패턴 | 결제 성공과 외부 전송 성공의 시점이 다를 수 있음을 기록으로 관리합니다. 전송 실패가 결제를 롤백시키지 않으며 재시도가 가능합니다. |
| 실제 주문 기반 인기 메뉴 조회 | 과제 규모에서는 별도 집계 테이블 없이 정확한 주문 기록을 기준으로 계산할 수 있습니다. `ordered_at`과 `menu_id` 인덱스를 둡니다. |
| Request/Response DTO 분리 | API 입력·출력 형식을 명확히 하고 JPA Entity를 응답에 직접 노출하지 않습니다. |
| 공통 응답·예외 형식 | 클라이언트가 성공 결과와 `ErrorCode`를 일관된 방식으로 처리할 수 있습니다. |

Outbox 방식은 전송 실패 후 재시도하므로 같은 주문 데이터가 두 번 전달될 가능성이 있습니다. 전송 시 Outbox ID를 이벤트 식별값으로 함께 전달해 중복을 구별할 수 있도록 설계합니다.

## 테스트

테스트는 test 프로필의 H2 인메모리 DB를 사용합니다. 테스트 DB는 실행 시 생성되며 로컬 MySQL을 사용하지 않습니다.
H2의 MySQL 모드는 SQL 호환을 위한 설정이며, 실제 MySQL의 락 동작을 검증한 결과를 의미하지 않습니다.

Java 21 환경에서 coffee-shop 디렉터리의 다음 명령으로 실행합니다.

```bash
./gradlew test
```

Windows에서는 다음 명령을 사용합니다.

```powershell
.\gradlew.bat test
```

### 검증 결과

2026-10-05 실행 결과: **29개 통과, 실패 0개, 오류 0개, 제외 0개**입니다.

| 테스트 클래스 | 개수 | 검증 내용 |
|---|---:|---|
| CoffeeShopApplicationTests | 1 | test 프로필에서 애플리케이션 구동 |
| PointWalletTest | 6 | 충전·차감, 잘못된 충전 금액, 잔액 부족, 오버플로 |
| OrderOutboxTest | 4 | 초기 상태, 처리 시작, 성공·실패 상태 변경 |
| CoffeeWorkflowIntegrationTest | 17 | 5개 API, 이력 저장, 가격 보존, 최근 7일 집계, UNIQUE 제약 |
| OrderRollbackIntegrationTest | 1 | Outbox 저장 실패 시 차감·주문·이력 롤백 |

아직 검증하지 않은 항목은 다음과 같습니다.

- 실제 MySQL에서 동시 주문 및 충전 시 잔액 정합성
- 외부 HTTP 전송 실패와 재시도
- 여러 전송 작업자의 중복 처리 방지
- 중단된 PROCESSING 기록 복구

## 트러블슈팅

실제로 실패한 테스트와 수정 내용을 문제별로 기록했습니다.

- [Outbox 대기 기록에 처리 시작 시각이 저장되는 문제](https://app.notion.com/p/3f0b4dedb89481e5bc45e3be4063b5d2)
- [Outbox 전송 메서드에서 상태 변경이 누락된 문제](https://app.notion.com/p/3f0b4dedb89481a69440ed81d79fc9b8)

수정 전 OrderOutboxTest 4개가 실패했으며, 수정 후 동일한 4개가 통과했습니다.
주문 롤백 테스트에서 의도적으로 발생시킨 Outbox 저장 예외는 검증용 상황이며, 실제 장애 기록으로 작성하지 않았습니다.
