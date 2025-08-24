DROP TABLE IF EXISTS owner CASCADE;
DROP TABLE IF EXISTS pet CASCADE;
DROP TABLE IF EXISTS pet_type CASCADE;
DROP TABLE IF EXISTS specialty CASCADE;
DROP TABLE IF EXISTS vet CASCADE;
DROP TABLE IF EXISTS vet_specialty CASCADE;
DROP TABLE IF EXISTS visit CASCADE;
DROP VIEW IF EXISTS owner_view;
DROP VIEW IF EXISTS visit_view;

CREATE TABLE owner (
    id serial PRIMARY KEY,
    first_name varchar(255),
    last_name varchar(255),
    address varchar(255),
    city varchar(255),
    telephone varchar(255),
    version integer DEFAULT 0
);

CREATE SEQUENCE pet_id_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE pet (
    id integer PRIMARY KEY DEFAULT nextval('pet_id_seq'),
    name varchar(255),
    birth_date date,
    owner_id integer,
    type_id integer
);

CREATE TABLE pet_type (
    id serial PRIMARY KEY,
    name varchar(255),
    description varchar(255),
    UNIQUE(name)
);

-- For specialty, we retain an integer type so you can explicitly insert IDs.
CREATE TABLE specialty (
    id integer PRIMARY KEY,
    name varchar(255),
    UNIQUE(name)
);

CREATE TABLE vet (
    id serial PRIMARY KEY,
    first_name varchar(255),
    last_name varchar(255)
);

CREATE TABLE vet_specialty (
    vet_id integer,
    specialty_id integer NOT NULL,
    PRIMARY KEY (vet_id, specialty_id)
);

CREATE TABLE visit (
    id serial PRIMARY KEY,
    visit_date date,
    description varchar(255),
    pet_id integer NOT NULL,
    "timestamp" timestamp DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pet 
    ADD CONSTRAINT pet_owner_fk FOREIGN KEY (owner_id) REFERENCES owner (id);

ALTER TABLE pet 
    ADD CONSTRAINT pet_pet_type_fk FOREIGN KEY (type_id) REFERENCES pet_type (id);

ALTER TABLE vet_specialty 
    ADD CONSTRAINT vet_specialty_specialty_fk FOREIGN KEY (specialty_id) REFERENCES specialty (id);

ALTER TABLE vet_specialty 
    ADD CONSTRAINT vet_specialty_vet_fk FOREIGN KEY (vet_id) REFERENCES vet (id);

ALTER TABLE visit 
    ADD CONSTRAINT visit_pet_fk FOREIGN KEY (pet_id) REFERENCES pet (id);

CREATE VIEW owner_view AS 
    SELECT * FROM owner;

CREATE VIEW visit_view AS 
    SELECT visit_date, description, pet_id, "timestamp" FROM visit;

-- Data Inserts

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

INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Leo', '2020-09-07', 1, 1);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Basil', '2022-08-06', 2, 6);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Rosy', '2021-04-17', 3, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Jewel', '2020-03-07', 3, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Iggy', '2020-11-30', 4, 3);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('George', '2020-01-20', 5, 4);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Samantha', '2022-09-04', 6, 1);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Max', '2022-09-04', 6, 1);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Lucky', '2021-08-06', 7, 5);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Mulligan', '2007-02-24', 8, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Freddy', '2020-03-09', 9, 5);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Lucky', '2020-06-24', 10, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id) VALUES ('Sly', '2022-06-08', NULL, 1);

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