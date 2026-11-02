ALTER TABLE radio_programme RENAME COLUMN kuku_id TO external_id;
ALTER TABLE radio_programme ADD COLUMN station VARCHAR(16) NOT NULL DEFAULT 'KUKU';

ALTER TABLE radio_airing RENAME COLUMN kuku_id TO external_id;
ALTER TABLE radio_airing ADD COLUMN station VARCHAR(16) NOT NULL DEFAULT 'KUKU';
