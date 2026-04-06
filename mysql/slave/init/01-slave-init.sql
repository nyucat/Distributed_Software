-- 延迟 10 秒以确保主库已经初始化完成
SELECT SLEEP(10);

-- 配置主库连接
CHANGE MASTER TO 
  MASTER_HOST='mysql-master',
  MASTER_USER='repl',
  MASTER_PASSWORD='repl_password',
  MASTER_AUTO_POSITION=1, -- 使用 GTID，如果 MySQL 8.0 没有开启 GTID，请使用传统位点或者修改配置
  GET_MASTER_PUBLIC_KEY=1;

-- 启动同步
START SLAVE;
