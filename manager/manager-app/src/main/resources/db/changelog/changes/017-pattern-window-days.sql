-- Persist configured analysis window on each pattern cluster snapshot.
ALTER TABLE topic_patterns ADD COLUMN window_days INT;

UPDATE topic_patterns
SET window_days = GREATEST(
    1,
    ROUND(EXTRACT(EPOCH FROM (period_end - period_start)) / 86400.0)::INT
)
WHERE window_days IS NULL;

UPDATE topic_patterns SET window_days = 30 WHERE window_days IS NULL;

ALTER TABLE topic_patterns ALTER COLUMN window_days SET DEFAULT 30;
ALTER TABLE topic_patterns ALTER COLUMN window_days SET NOT NULL;
