CREATE TABLE IF NOT EXISTS meeting_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    capacity INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_meeting_room_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
