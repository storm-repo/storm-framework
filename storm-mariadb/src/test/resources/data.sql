SET FOREIGN_KEY_CHECKS = 0;

drop table if exists owner CASCADE;
drop table if exists pet CASCADE;
drop table if exists pet_type CASCADE;
drop table if exists specialty CASCADE;
drop table if exists vet CASCADE;
drop table if exists vet_specialty CASCADE;
drop table if exists visit CASCADE;
drop view if exists owner_view;
drop view if exists visit_view;

CREATE SEQUENCE pet_id_seq
    START WITH 1
    INCREMENT BY 1;

create table owner (id integer auto_increment, first_name varchar(255), last_name varchar(255), address varchar(255), city varchar(255), telephone varchar(255), primary key (id), version integer default 0);
create table pet (id integer PRIMARY KEY DEFAULT(NEXT VALUE for pet_id_seq), name varchar(255), birth_date date, owner_id integer, type_id integer);
create table pet_type (id integer auto_increment, name varchar(255), description varchar(255), primary key (id), unique(name));
create table specialty (id integer, name varchar(255), primary key (id), unique(name));
create table vet (id integer auto_increment, first_name varchar(255), last_name varchar(255), primary key (id));
create table vet_specialty (vet_id integer, specialty_id integer not null, primary key (vet_id, specialty_id));
create table visit (id integer auto_increment, visit_date date, description varchar(255), pet_id integer not null, timestamp timestamp default CURRENT_TIMESTAMP, primary key (id));
alter table pet add constraint pet_owner_fk foreign key (owner_id) references owner (id);
alter table pet add constraint pet_pet_type_fk foreign key (type_id) references pet_type (id);
alter table vet_specialty add constraint vet_specialty_specialty_fk foreign key (specialty_id) references specialty (id);
alter table vet_specialty add constraint vet_specialty_vet_fk foreign key (vet_id) references vet (id);
alter table visit add constraint visit_pet_fk foreign key (pet_id) references pet (id);
create view owner_view as select * from owner;
create view visit_view as select visit_date, description, pet_id, timestamp from visit;

INSERT INTO vet (first_name, last_name) VALUES ('James', 'Carter');
INSERT INTO vet (first_name, last_name) VALUES ('Helen', 'Leary');
INSERT INTO vet (first_name, last_name) VALUES ('Linda', 'Douglas');
INSERT INTO vet (first_name, last_name) VALUES ('Rafael', 'Ortega');
INSERT INTO vet (first_name, last_name) VALUES ('Henry', 'Stevens');
INSERT INTO vet (first_name, last_name) VALUES ('Sharon', 'Jenkins');

INSERT INTO specialty (id, name) VALUES (1, 'radiology');
INSERT INTO specialty (id, name) VALUES (2, 'surgery');
INSERT INTO specialty (id, name) VALUES (3, 'dentistry');

INSERT INTO vet_specialty (vet_id, specialty_id) VALUES (2, 1);
INSERT INTO vet_specialty (vet_id, specialty_id) VALUES (3, 2);
INSERT INTO vet_specialty (vet_id, specialty_id) VALUES (3, 3);
INSERT INTO vet_specialty (vet_id, specialty_id) VALUES (4, 2);
INSERT INTO vet_specialty (vet_id, specialty_id) VALUES (5, 1);

INSERT INTO pet_type (name) VALUES ('cat');
INSERT INTO pet_type (name) VALUES ('dog');
INSERT INTO pet_type (name) VALUES ('lizard');
INSERT INTO pet_type (name) VALUES ('snake');
INSERT INTO pet_type (name) VALUES ('bird');
INSERT INTO pet_type (name) VALUES ('hamster');

INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435');
INSERT INTO owner (first_name, last_name, address, city, telephone) VALUES ('Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487');

INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Leo', '2020-09-07', 1, 1);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Basil', '2022-08-06', 2, 6);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Rosy', '2021-04-17', 3, 2);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Jewel', '2020-03-07', 3, 2);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Iggy', '2020-11-30', 4, 3);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('George', '2020-01-20', 5, 4);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Samantha', '2022-09-04', 6, 1);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Max', '2022-09-04', 6, 1);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Lucky', '2021-08-06', 7, 5);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Mulligan', '2007-02-24', 8, 2);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Freddy', '2020-03-09', 9, 5);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Lucky', '2020-06-24', 10, 2);
INSERT INTO pet (name, birth_date, owner_id , type_id) VALUES ('Sly', '2022-06-08', NULL, 1);

INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-01', 'rabies shot', 7);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-02', 'rabies shot', 8);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-03', 'neutered', 8);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-04', 'spayed', 1);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-04', 'spayed', 2);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-06', 'spayed', 3);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-08', 'neutered', 4);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-08', 'spayed', 1);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-08', 'neutered', 2);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-09', 'spayed', 4);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-12', 'rabies shot', 5);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-13', 'spayed', 6);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-13', 'rabies shot', 6);
INSERT INTO visit (visit_date, description, pet_id) VALUES ('2023-01-13', 'spayed', 7);


-- UUID support tests
DROP TABLE IF EXISTS api_key;
CREATE TABLE api_key (id char(36) PRIMARY KEY, name varchar(255) NOT NULL, external_reference char(36));

INSERT INTO api_key (id, name, external_reference) VALUES ('550e8400-e29b-41d4-a716-446655440000', 'Default Key', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
INSERT INTO api_key (id, name, external_reference) VALUES ('6ba7b810-9dad-11d1-80b4-00c04fd430c8', 'Secondary Key', NULL);

-- Polymorphic tables for sealed type hierarchy tests.

-- Pattern A: Single-Table Inheritance
DROP TABLE IF EXISTS animal;
CREATE TABLE animal (id integer AUTO_INCREMENT PRIMARY KEY, dtype varchar(50) NOT NULL, name varchar(255), indoor boolean, weight integer);

INSERT INTO animal (dtype, name, indoor) VALUES ('Cat', 'Whiskers', true);
INSERT INTO animal (dtype, name, indoor) VALUES ('Cat', 'Luna', false);
INSERT INTO animal (dtype, name, weight) VALUES ('Dog', 'Rex', 30);
INSERT INTO animal (dtype, name, weight) VALUES ('Dog', 'Max', 15);

-- Pattern C: Joined Table Inheritance
drop table if exists joined_cat;
drop table if exists joined_dog;
drop table if exists joined_animal;
create table joined_animal (id integer auto_increment, dtype varchar(50) not null, name varchar(255), primary key (id));
create table joined_cat (id integer not null, indoor boolean, primary key (id));
create table joined_dog (id integer not null, weight integer, primary key (id));
alter table joined_cat add constraint joined_cat_animal_fk foreign key (id) references joined_animal (id);
alter table joined_dog add constraint joined_dog_animal_fk foreign key (id) references joined_animal (id);

INSERT INTO joined_animal (dtype, name) VALUES ('JoinedCat', 'Whiskers');
INSERT INTO joined_cat (id, indoor) VALUES (1, true);
INSERT INTO joined_animal (dtype, name) VALUES ('JoinedCat', 'Luna');
INSERT INTO joined_cat (id, indoor) VALUES (2, false);
INSERT INTO joined_animal (dtype, name) VALUES ('JoinedDog', 'Rex');
INSERT INTO joined_dog (id, weight) VALUES (3, 30);

-- Pattern D: Joined Table Inheritance without @Discriminator
drop table if exists nodsc_cat;
drop table if exists nodsc_dog;
drop table if exists nodsc_bird;
drop table if exists nodsc_animal;
create table nodsc_animal (id integer auto_increment, name varchar(255), primary key (id));
create table nodsc_cat (id integer not null, indoor boolean, primary key (id));
create table nodsc_dog (id integer not null, weight integer, primary key (id));
create table nodsc_bird (id integer not null, primary key (id));
alter table nodsc_cat add constraint nodsc_cat_animal_fk foreign key (id) references nodsc_animal (id);
alter table nodsc_dog add constraint nodsc_dog_animal_fk foreign key (id) references nodsc_animal (id);
alter table nodsc_bird add constraint nodsc_bird_animal_fk foreign key (id) references nodsc_animal (id);

INSERT INTO nodsc_animal (name) VALUES ('Whiskers');
INSERT INTO nodsc_cat (id, indoor) VALUES (1, true);
INSERT INTO nodsc_animal (name) VALUES ('Luna');
INSERT INTO nodsc_cat (id, indoor) VALUES (2, false);
INSERT INTO nodsc_animal (name) VALUES ('Rex');
INSERT INTO nodsc_dog (id, weight) VALUES (3, 30);
INSERT INTO nodsc_animal (name) VALUES ('Tweety');
INSERT INTO nodsc_bird (id) VALUES (4);

SET FOREIGN_KEY_CHECKS = 1;