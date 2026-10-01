# 비동기 Excel Export

10만 건 주문 데이터를 요청 흐름과 분리해 XLSX 파일로 생성하는 Playstory 사전 과제입니다. 자동 시드 데이터는 과제 명세의 `id`, `user_name`, `product_name`, `category`, `amount`, `status`, `order_date` 컬럼을 사용합니다.

## 실행

```bash
docker compose up --build
```

브라우저에서 `http://localhost:8088`을 열고 **새 엑셀 생성 요청**을 누릅니다. PostgreSQL schema와 10만 건 시드는 Flyway migration으로 자동 준비됩니다.

## 구성

- `frontend`: React/Vite 결과물을 Nginx가 제공하고 `/api`를 backend로 proxy
- `backend`: Spring Boot 단일 컨테이너. `POST /api/export-jobs`는 job을 저장한 뒤 `202 Accepted`를 즉시 반환
- `postgres`: 주문 데이터와 job 상태를 저장
- `exports_data`: 생성된 xlsx가 backend 재시작 후에도 남는 named volume

Backend에는 CPU 0.5, memory 1GB 제한과 CPU 0.25, memory 512MB reservation을 Compose에 명시했습니다.

## 버전 고정 원칙

- Spring Boot parent는 `3.4.5`로 고정해 Spring framework·test starter의 호환 버전을 BOM으로 일괄 관리한다.
- 직접 사용하는 Flyway core/PostgreSQL module은 `10.20.1`, PostgreSQL JDBC driver는 `42.7.13`, Apache POI는 `5.5.1`로 명시한다.
- 모든 Docker base image는 정확한 태그와 multi-architecture manifest digest를 함께 고정한다. 따라서 같은 Dockerfile은 시간에 따라 다른 base image를 받지 않는다.

## 상태와 복구

job 상태는 `pending`, `processing`, `done`, `failed`입니다. scheduler worker는 한 번에 하나의 `PENDING` job을 `FOR UPDATE SKIP LOCKED`로 선점합니다. PostgreSQL cursor streaming과 Apache POI `SXSSFWorkbook`으로 전체 10만 행을 메모리에 적재하지 않습니다.

파일은 `.tmp`에 생성한 뒤 최종 위치로 원자 이동하며, ZIP 형식 검증을 통과한 뒤에만 `DONE`으로 전이합니다. worker가 종료돼 `PROCESSING`에 남은 job은 lease 만료 후 재처리합니다. 시작 시 `DONE`인데 파일이 없거나 손상된 경우 `FAILED`로 보정합니다.

## 선택과 한계

- DB job queue는 이 과제에서 외부 broker 없이 영속성·복구를 제공한다. 같은 DB를 worker가 읽으므로 DB 변경과 메시지 발행의 이중 쓰기가 없어 Outbox는 적용하지 않았다.
- Command(`POST`, worker 상태 전이)와 Query(목록·다운로드)는 논리적으로 분리한다. 단순 job 목록에 read model을 별도로 두는 물리적 CQRS는 projection 지연과 운영 복잡도가 더 커 적용하지 않았다.
- 현재는 단일 worker와 polling을 선택했다. 실제 queue wait 증가가 관측되면 worker 분리·병렬화, 객체 스토리지, Outbox, CQRS read model을 단계적으로 검토한다.
- 실패 job 자동 재시도, 사용자 인증, 보존 기간 기반 파일 정리는 후속 개선 항목이다.
