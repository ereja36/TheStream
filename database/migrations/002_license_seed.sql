-- Licenca minimale e nevojshme që API get_config të mos kthejë gabim 500
-- Ekzekuto vetëm nëse tabela tbl_license është bosh

INSERT INTO `tbl_license` (`id`, `purchase_code`, `item_id`, `item_name`, `buyer`, `license_type`, `purchase_date`)
SELECT 1, 'shif-shqip-local', 1, 'Shif Shqip', 'local', 'regular', '2025-01-01'
WHERE NOT EXISTS (SELECT 1 FROM `tbl_license` LIMIT 1);
