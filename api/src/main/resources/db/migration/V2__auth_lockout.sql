-- V2: cửa sổ đếm đăng nhập sai (T04 mục 3.2: 5 lần sai trong 15 phút → khóa 15 phút).
-- Hoàn tác: ALTER TABLE users DROP COLUMN failed_window_start;
ALTER TABLE users ADD COLUMN failed_window_start timestamptz NULL;
