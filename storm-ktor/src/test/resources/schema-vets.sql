drop table if exists vet CASCADE;

create table vet (id integer auto_increment, first_name varchar(255) not null, last_name varchar(255) not null, primary key (id));

INSERT INTO vet (first_name, last_name) VALUES ('James', 'Carter');
INSERT INTO vet (first_name, last_name) VALUES ('Helen', 'Leary');
