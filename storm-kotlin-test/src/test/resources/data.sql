drop table if exists city CASCADE;

create table city (id integer auto_increment, name varchar(255), primary key (id));

insert into city (name) values ('Amsterdam');
insert into city (name) values ('Rotterdam');
insert into city (name) values ('Utrecht');
