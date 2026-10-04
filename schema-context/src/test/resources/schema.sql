CREATE TABLE companies (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    company_id INTEGER NOT NULL,
    email VARCHAR(100),
    FOREIGN KEY(company_id) REFERENCES companies(id)
);