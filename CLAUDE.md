# NHN 경비 관리 시스템

## 개요
Spring Boot 기반 경비 관리 웹 애플리케이션. 경비 등록/수정/삭제, 예산 관리, Excel 업로드/다운로드 기능.

## Tech Stack
- Spring Boot 3.2.5, Java 17, Maven
- MySQL 8.4.8 (service: MySQL84, root/drager21, db: test_db)
- Thymeleaf, Spring Security (admin/admin123 ROLE_ADMIN, bugs/bugs123 ROLE_USER)
- Apache POI 5.2.5, Pretendard 폰트

## Build & Run
```bash
# Maven 빌드
C:\tools\maven\apache-maven-3.9.12\bin\mvn.cmd clean package -q -DskipTests

# 서버 실행
java -jar target/product-manager-0.0.1-SNAPSHOT.jar

# 헬퍼 스크립트
C:\Users\Bugs\build.ps1
C:\Users\Bugs\restart.ps1
```

## 주요 구조
- **Controller**: ExpenseController.java (경비 CRUD, 예산 CRUD, 업로드, 다운로드)
- **Service**: ExpenseService, BudgetService (JPA Specification 동적 쿼리), ExcelService (멀티시트)
- **Repository**: ExpenseRepository, BudgetRepository (JpaSpecificationExecutor)
- **Templates**: expense/ (list.html, form.html, upload-form.html, budget-form.html)
- **Security**: SecurityConfig.java (InMemoryUserDetailsManager)

## 주요 기능
- 월 다중선택 (체크박스 드롭다운, List<String> IN clause)
- 분류/구분 필터 + 상세조건 검색 (상호/용도 LIKE)
- Excel 업로드: 멀티시트, 년월 자동추출 (시트명/날짜셀)
- Excel 다운로드: 섹션 기반 포맷
- 클라이언트 정렬 (날짜 default DESC)

## 주의사항
- DB URL에 `allowPublicKeyRetrieval=true` 필수
- CSRF: 로그아웃은 POST, form에 CSRF 토큰 필요
- PowerShell에서 `&&` 사용 불가 → 명령 분리 실행
