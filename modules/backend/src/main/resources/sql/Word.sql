CREATE TABLE Word
(
    id         INTEGER      NOT NULL,
    latin      VARCHAR(255) NOT NULL,
    cz         VARCHAR(255),
    en         VARCHAR(255),
    PRIMARY KEY (id),
    FOREIGN KEY (id) REFERENCES JakonObject (id) ON DELETE CASCADE
)