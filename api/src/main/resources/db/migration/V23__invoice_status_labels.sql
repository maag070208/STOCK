-- Two invoice statuses were renamed with the receiving redesign (material-receiving.md section 8): IN_ASSIGNMENT now
-- means every line is counted and the work is ready to place, and CLOSED now means every line is received and placed.
-- The catalog seeder only inserts what is missing, so an installation that already holds the statuses keeps the old
-- names; this updates them, and only while they still hold the seeded value, so a name an administrator chose stays.
update catalog_item_label l
set label = case l.locale when 'es' then 'Por ubicar' else 'Ready to place' end
where l.catalog_item_id in (
    select i.id from catalog_item i join catalog c on c.id = i.catalog_id
    where c.code = 'INVOICE_STATUS' and i.code = 'IN_ASSIGNMENT'
)
  and ((l.locale = 'es' and l.label = 'En asignación') or (l.locale = 'en' and l.label = 'In assignment'));

update catalog_item_label l
set label = case l.locale when 'es' then 'Completada' else 'Completed' end
where l.catalog_item_id in (
    select i.id from catalog_item i join catalog c on c.id = i.catalog_id
    where c.code = 'INVOICE_STATUS' and i.code = 'CLOSED'
)
  and ((l.locale = 'es' and l.label = 'Cerrada') or (l.locale = 'en' and l.label = 'Closed'));
