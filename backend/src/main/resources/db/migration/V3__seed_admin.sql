-- Hash comes from the admin_password_hash Flyway placeholder (config), never from this file.
INSERT INTO app_user (phone, password_hash, name, role)
VALUES ('9999999999', '${admin_password_hash}', 'Admin', 'ADMIN');
