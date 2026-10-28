-- Khởi tạo khi cài PostgreSQL 16 trực tiếp trên máy (cách b trong README). Chạy bằng superuser:
--   psql -U postgres -f ops/db/init.sql
-- Docker (cách a) không cần file này: image tự tạo role/DB, migration V1 tạo extension.
-- Đổi mật khẩu cho khớp DB_PASSWORD trong .env.

CREATE ROLE chatdoc WITH LOGIN PASSWORD 'change-me';
CREATE DATABASE chatdoc OWNER chatdoc ENCODING 'UTF8' TEMPLATE template0;

\connect chatdoc

-- Extension cần quyền superuser nên tạo sẵn ở đây; migration V1 dùng IF NOT EXISTS.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;

ALTER SCHEMA public OWNER TO chatdoc;
