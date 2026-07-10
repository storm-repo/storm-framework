drop table if exists pet CASCADE;
drop table if exists pet_type CASCADE;
drop table if exists vet CASCADE;

create table vet (id integer auto_increment, first_name varchar(255) not null, last_name varchar(255) not null, primary key (id));

create table pet_type (id integer, name varchar(255) not null, primary key (id));
create table pet (id integer auto_increment, name varchar(255) not null, type_id integer not null, primary key (id));
alter table pet add constraint pet_pet_type_fk foreign key (type_id) references pet_type (id);

INSERT INTO pet_type (id, name) VALUES (1, 'Cat');
INSERT INTO pet_type (id, name) VALUES (2, 'Dog');
INSERT INTO pet (name, type_id) VALUES ('Leo', 1);
INSERT INTO pet (name, type_id) VALUES ('Basil', 2);
INSERT INTO pet (name, type_id) VALUES ('Rosy', 2);
