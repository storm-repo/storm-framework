-- Schema for the container database tests: plain DDL that every dialect accepts, without drop guards, so it only
-- runs twice within a container when each test class receives a database of its own.
create table item (id integer primary key, name varchar(255));
