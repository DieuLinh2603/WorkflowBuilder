ALTER TABLE data_pipelines ALTER COLUMN timezone SET DEFAULT 'Asia/Ho_Chi_Minh';
UPDATE data_pipelines
SET timezone = 'Asia/Ho_Chi_Minh'
WHERE timezone IS NULL OR timezone = 'Asia/Bangkok';
