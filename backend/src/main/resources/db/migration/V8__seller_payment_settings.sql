-- Where customers pay a seller (UPI) and the flat shipping fee added to each of that seller's orders.
ALTER TABLE seller_profile ADD COLUMN upi_id VARCHAR(100);
ALTER TABLE seller_profile ADD COLUMN shipping_fee INTEGER NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0 AND shipping_fee <= 10000);
