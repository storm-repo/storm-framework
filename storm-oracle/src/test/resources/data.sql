-- Create tables
CREATE TABLE owner (
                       id NUMBER GENERATED BY DEFAULT AS IDENTITY,
                       first_name VARCHAR2(255),
                       last_name VARCHAR2(255),
                       address VARCHAR2(255),
                       city VARCHAR2(255),
                       telephone VARCHAR2(255),
                       version NUMBER DEFAULT 0,
                       CONSTRAINT pk_owner PRIMARY KEY (id)
);

CREATE TABLE pet (
                     id NUMBER GENERATED BY DEFAULT AS IDENTITY,
                     name VARCHAR2(255),
                     birth_date DATE,
                     owner_id NUMBER,
                     type_id NUMBER,
                     CONSTRAINT pk_pet PRIMARY KEY (id)
);

CREATE TABLE pet_type (
                          id NUMBER GENERATED BY DEFAULT AS IDENTITY,
                          name VARCHAR2(255),
                          description VARCHAR2(255),
                          CONSTRAINT pk_pet_type PRIMARY KEY (id),
                          CONSTRAINT uq_pet_type_name UNIQUE (name)
);

CREATE TABLE specialty (
                           id NUMBER,
                           name VARCHAR2(255),
                           CONSTRAINT pk_specialty PRIMARY KEY (id),
                           CONSTRAINT uq_specialty_name UNIQUE (name)
);

CREATE TABLE vet (
                     id NUMBER GENERATED BY DEFAULT AS IDENTITY,
                     first_name VARCHAR2(255),
                     last_name VARCHAR2(255),
                     CONSTRAINT pk_vet PRIMARY KEY (id)
);

CREATE TABLE vet_specialty (
                               vet_id NUMBER,
                               specialty_id NUMBER NOT NULL,
                               CONSTRAINT pk_vet_specialty PRIMARY KEY (vet_id, specialty_id)
);

CREATE TABLE visit (
                       id NUMBER GENERATED BY DEFAULT AS IDENTITY,
                       visit_date DATE,
                       description VARCHAR2(255),
                       pet_id NUMBER NOT NULL,
                       visit_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT pk_visit PRIMARY KEY (id)
);

-- Add foreign key constraints
ALTER TABLE pet ADD CONSTRAINT pet_owner_fk FOREIGN KEY (owner_id) REFERENCES owner(id);
ALTER TABLE pet ADD CONSTRAINT pet_pet_type_fk FOREIGN KEY (type_id) REFERENCES pet_type(id);
ALTER TABLE vet_specialty ADD CONSTRAINT vet_specialty_specialty_fk FOREIGN KEY (specialty_id) REFERENCES specialty(id);
ALTER TABLE vet_specialty ADD CONSTRAINT vet_specialty_vet_fk FOREIGN KEY (vet_id) REFERENCES vet(id);
ALTER TABLE visit ADD CONSTRAINT visit_pet_fk FOREIGN KEY (pet_id) REFERENCES pet(id);

-- Create views
CREATE VIEW owner_view AS SELECT * FROM owner;
CREATE VIEW visit_view AS SELECT visit_date, description, pet_id, visit_ts FROM visit;

-- Delete from child tables first (to avoid foreign key constraint violations)
DELETE FROM vet_specialty;
DELETE FROM visit;
DELETE FROM pet;
DELETE FROM vet;
DELETE FROM specialty;
DELETE FROM pet_type;
DELETE FROM owner;

-- Reset identity sequences (Oracle 12c+)
ALTER TABLE owner MODIFY id GENERATED AS IDENTITY (START WITH 1);
ALTER TABLE pet MODIFY id GENERATED AS IDENTITY (START WITH 1);
ALTER TABLE pet_type MODIFY id GENERATED AS IDENTITY (START WITH 1);
ALTER TABLE vet MODIFY id GENERATED AS IDENTITY (START WITH 1);
ALTER TABLE visit MODIFY id GENERATED AS IDENTITY (START WITH 1);

-- Insert data
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

INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435');
INSERT INTO owner (first_name, last_name, address, city, telephone)
VALUES ('Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487');

INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Leo', TO_DATE('2020-09-07','YYYY-MM-DD'), 1, 1);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Basil', TO_DATE('2022-08-06','YYYY-MM-DD'), 2, 6);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Rosy', TO_DATE('2021-04-17','YYYY-MM-DD'), 3, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Jewel', TO_DATE('2020-03-07','YYYY-MM-DD'), 3, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Iggy', TO_DATE('2020-11-30','YYYY-MM-DD'), 4, 3);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('George', TO_DATE('2020-01-20','YYYY-MM-DD'), 5, 4);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Samantha', TO_DATE('2022-09-04','YYYY-MM-DD'), 6, 1);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Max', TO_DATE('2022-09-04','YYYY-MM-DD'), 6, 1);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Lucky', TO_DATE('2021-08-06','YYYY-MM-DD'), 7, 5);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Mulligan', TO_DATE('2007-02-24','YYYY-MM-DD'), 8, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Freddy', TO_DATE('2020-03-09','YYYY-MM-DD'), 9, 5);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Lucky', TO_DATE('2020-06-24','YYYY-MM-DD'), 10, 2);
INSERT INTO pet (name, birth_date, owner_id, type_id)
VALUES ('Sly', TO_DATE('2022-06-08','YYYY-MM-DD'), NULL, 1);

INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-01','YYYY-MM-DD'), 'rabies shot', 7);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-02','YYYY-MM-DD'), 'rabies shot', 8);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-03','YYYY-MM-DD'), 'neutered', 8);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-04','YYYY-MM-DD'), 'spayed', 1);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-04','YYYY-MM-DD'), 'spayed', 2);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-06','YYYY-MM-DD'), 'spayed', 3);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-08','YYYY-MM-DD'), 'neutered', 4);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-08','YYYY-MM-DD'), 'spayed', 1);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-08','YYYY-MM-DD'), 'neutered', 2);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-09','YYYY-MM-DD'), 'spayed', 4);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-12','YYYY-MM-DD'), 'rabies shot', 5);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-13','YYYY-MM-DD'), 'spayed', 6);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-13','YYYY-MM-DD'), 'rabies shot', 6);
INSERT INTO visit (visit_date, description, pet_id)
VALUES (TO_DATE('2023-01-13','YYYY-MM-DD'), 'spayed', 7);