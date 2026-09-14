# Workflow Builder

Ứng dụng web nội bộ giúp thiết kế, cấu hình, xuất bản và vận hành các quy trình nghiệp vụ. Hệ thống cung cấp trình thiết kế workflow trực quan, phân quyền theo vai trò, xử lý yêu cầu/phê duyệt, thông báo và data pipeline.

## Tính năng chính

- Thiết kế workflow trực quan bằng node và connection.
- Cấu hình các bước: bắt đầu, nghiệp vụ, phân công, phê duyệt, review, thông báo, system action và kết thúc.
- Kiểm tra tính hợp lệ, xuất bản, nhân bản và quản lý phiên bản workflow.
- Khởi tạo, theo dõi, xử lý và thu hồi yêu cầu.
- Quản lý người dùng, vai trò và phạm vi business module.
- Trung tâm thông báo và nhắc việc theo deadline.
- Tạo connector REST/PostgreSQL và thiết kế data pipeline.
- Đăng nhập JWT, đổi mật khẩu và khôi phục mật khẩu.
- Tài liệu API bằng Swagger/OpenAPI.

### Output tính toán trong Review và System Action

- Review: Owner có thể cấu hình công thức gợi ý. Reviewer thêm/sửa/xóa công thức trên task, bấm **Tính thử / Xem trước**, rồi gửi kết quả. Công thức và kết quả được lưu theo từng lượt review, kể cả khi chọn không đạt.
- System Action: chọn **Tính cột output** để hệ thống tự tính và đưa cột số vào dữ liệu chuyển sang bước sau.
- Mỗi output gồm tên hiển thị, mã cột mới và công thức. Mã cột bắt đầu bằng chữ, chỉ chứa chữ, số và `_`. Output tính từ trên xuống, có thể tham chiếu output phía trên.
- Ví dụ: `[so_luong] * [don_gia]`, `([doanh_thu] - [chi_phi]) / [doanh_thu] * 100`, `SUM([thanh_tien])`, `AVG([diem])`. Có thể dùng `AVERAGE` thay `AVG`.
- Phép toán thường tính trên từng dòng. SUM/AVG trong Review batch dùng các dòng của task hiện tại; trong System Action batch dùng dữ liệu các dòng của yêu cầu batch tại thời điểm chạy. Kết quả tổng hợp được thêm vào mỗi dòng. Yêu cầu đơn hỗ trợ tổng/trung bình giá trị hoặc danh sách số của cột.
- Không ghi đè cột nguồn. Dữ liệu trống, không phải số, công thức sai hoặc chia cho 0 sẽ báo lỗi. System Action dùng nhánh/xử lý lỗi đã cấu hình.
- Công thức tính bằng số thập phân với độ chính xác 16 chữ số có nghĩa. Sau khi cập nhật, khởi động lại backend để Flyway áp dụng migration V12.

## Công nghệ sử dụng

| Thành phần | Công nghệ |
| --- | --- |
| Frontend | React 18, Vite 5, React Router, Tailwind CSS, XYFlow, Axios |
| Backend | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database | PostgreSQL, Flyway |
| Khác | JWT, MapStruct, Lombok, ShedLock, OpenAPI |
| Kiểm thử | JUnit 5, Spring Boot Test, Testcontainers |

## Cấu trúc dự án

```text
workflow-builder/
├── backend/                  # REST API Spring Boot
│   ├── src/main/java/        # Controller, service, entity, repository...
│   ├── src/main/resources/   # Cấu hình và Flyway migration
│   └── src/test/             # Unit/integration tests
├── frontend/                 # Giao diện React/Vite
│   ├── public/
│   └── src/
└── README.md
```

## Yêu cầu môi trường

- Java Development Kit 17 trở lên.
- Maven 3.9 trở lên.
- Node.js 18 trở lên và npm.
- PostgreSQL 14 trở lên.

## Cài đặt và chạy local

### 1. Chuẩn bị database

Tạo database PostgreSQL:

```sql
CREATE DATABASE workflow_db;
```

Mặc định backend sử dụng PostgreSQL tại `localhost:5432`, database `workflow_db`, username `postgres` và password `123456789`. Bạn có thể thay đổi bằng các biến môi trường `DB_URL`, `DB_USERNAME` và `DB_PASSWORD`.

### 2. Chạy backend

```bash
cd backend
mvn spring-boot:run
```

Backend chạy tại `http://localhost:8081`. Flyway sẽ tự động cập nhật cấu trúc database và nạp dữ liệu phát triển khi profile `local` được sử dụng.

Swagger UI: `http://localhost:8081/swagger-ui.html`

### 3. Chạy frontend

Mở một terminal khác:

```bash
cd frontend
npm install
npm run dev
```

Truy cập ứng dụng tại `http://localhost:5174`. Vite tự động chuyển tiếp các request `/api` đến backend ở cổng `8081`.

## Biến môi trường

| Biến | Bắt buộc | Giá trị local mặc định | Mô tả |
| --- | --- | --- | --- |
| `DB_URL` | Không | `jdbc:postgresql://localhost:5432/workflow_db` | JDBC URL của PostgreSQL |
| `DB_USERNAME` | Không | `postgres` | Tài khoản database |
| `DB_PASSWORD` | Không | `123456789` | Mật khẩu database ở profile local |
| `JWT_SECRET` | Production | Secret local có sẵn | Khóa ký JWT, nên dài ít nhất 32 byte |
| `JWT_EXPIRATION_MS` | Không | `86400000` | Thời hạn JWT (24 giờ) |
| `PIPELINE_ENCRYPTION_KEY` | Production | Dùng `JWT_SECRET` | Khóa mã hóa thông tin connector |
| `PIPELINE_MAX_RECORDS` | Không | `10000` | Số record tối đa mỗi lần chạy pipeline |
| `PIPELINE_QUERY_TIMEOUT_SECONDS` | Không | `30` | Thời gian chờ truy vấn pipeline |
| `EXPOSE_PASSWORD_RESET_LINK` | Không | `true` ở local | Trả link reset trong môi trường phát triển |
| `FLYWAY_BASELINE_ON_MIGRATE` | Không | `false` | Baseline database cũ khi migrate |
| `FLYWAY_BASELINE_VERSION` | Không | `1` | Phiên bản baseline của Flyway |
| `FLYWAY_OUT_OF_ORDER` | Không | `false` | Cho phép chạy migration không đúng thứ tự |

> Không sử dụng các giá trị secret mặc định khi triển khai production.

## Các lệnh thường dùng

### Backend

```bash
cd backend
mvn spring-boot:run   # Chạy ứng dụng
mvn test              # Chạy test
mvn clean package     # Build file JAR
```

### Frontend

```bash
cd frontend
npm run dev           # Chạy development server
npm run build         # Build production
npm run preview       # Xem thử production build
```

## Chạy với cấu hình production

Thiết lập tối thiểu các biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` và `PIPELINE_ENCRYPTION_KEY`, sau đó chạy:

```bash
cd backend
mvn clean package
java -jar target/workflow-builder-1.0.0.jar --spring.profiles.active=prod
```

Build frontend bằng `npm run build`; thư mục đầu ra là `frontend/dist` và có thể được triển khai trên một static web server. Khi triển khai riêng frontend và backend, cần cấu hình reverse proxy để chuyển `/api` đến backend.

## API

Các nhóm endpoint chính:

- `/api/auth`: đăng nhập và quản lý mật khẩu.
- `/api/users`: quản lý người dùng.
- `/api/workflows`: quản lý, thiết kế và xuất bản workflow.
- `/api/instances`, `/api/tasks`, `/api/my-tasks`: vận hành workflow.
- `/api/notifications`: thông báo người dùng.
- `/api/connectors`, `/api/pipelines`: connector và data pipeline.
- `/api/metadata`, `/api/settings`: metadata và cấu hình hệ thống.

Sau khi backend khởi động, xem đầy đủ request/response tại Swagger UI.

## Lưu ý phát triển

- Profile mặc định là `local`.
- Không sửa các migration Flyway đã được áp dụng; hãy tạo migration mới trong `backend/src/main/resources/db/migration`.
- Dữ liệu tài khoản demo chỉ được nạp từ `backend/src/main/resources/db/devdata` ở profile local.
- Không commit mật khẩu database, JWT secret hoặc credential của connector vào Git.

## License

Dự án được phát triển cho mục đích nội bộ. Cập nhật phần này nếu dự án được phát hành với một giấy phép cụ thể.

