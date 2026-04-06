-- 1. 创建同步账号
CREATE USER 'repl'@'%' IDENTIFIED WITH mysql_native_password BY 'repl_password';
-- 2. 授权
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;

-- 后面 Docker 会自动执行外部映射的 schema.sql 建表
