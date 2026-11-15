-- Paleiskite vieną kartą kaip PostgreSQL administratorius per psql.
-- Slaptažodžiai įvedami interaktyviai; jie neįrašomi į šį failą.
CREATE ROLE dotask LOGIN;
\password dotask
CREATE DATABASE dotask OWNER dotask;
CREATE ROLE dotask_test LOGIN;
\password dotask_test
CREATE DATABASE dotask_test OWNER dotask_test;
