CREATE USER 'repl'@'%' IDENTIFIED WITH mysql_native_password BY 'repl1234';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
