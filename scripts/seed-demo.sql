-- Las condiciones de pago que usa el negocio. El catálogo venía con un valor de prueba ("askdm"), así que se siembran
-- las de verdad: contado y crédito a 30 y 60 días.
insert into catalog_item (id, catalog_id, code, created_at, updated_at)
select gen_random_uuid(), c.id, v.code, now(), now()
from catalog c
join (values ('CONTADO'), ('CREDITO_30'), ('CREDITO_60')) as v(code) on true
where c.code = 'PAYMENT_TERMS'
on conflict do nothing;

insert into catalog_item_label (catalog_item_id, locale, label)
select i.id, l.locale, l.label
from catalog_item i
join catalog c on c.id = i.catalog_id and c.code = 'PAYMENT_TERMS'
join (values
	('CONTADO', 'es', 'Contado'), ('CONTADO', 'en', 'Cash'),
	('CREDITO_30', 'es', 'Crédito 30 días'), ('CREDITO_30', 'en', 'Credit 30 days'),
	('CREDITO_60', 'es', 'Crédito 60 días'), ('CREDITO_60', 'en', 'Credit 60 days')
) as l(code, locale, label) on l.code = i.code
on conflict do nothing;

-- Los proveedores: aceros, plomería, eléctrico y tornillería, con sus contactos.
insert into suppliers (id, code, name, tax_id, street, exterior_number, interior_number, neighborhood, city, state_id, postal_code, country_id, payment_terms_id, active, created_at, updated_at)
select gen_random_uuid(), v.code, v.name, v.tax_id, v.street, v.exterior, v.interior, v.neighborhood, v.city,
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'STATE' and i.code = v.state),
	v.postal,
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'COUNTRY' and i.code = 'MX'),
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'PAYMENT_TERMS' and i.code = v.terms),
	true, now(), now()
from (values
	('PR-ACN', 'Aceros del Norte, S.A. de C.V.', 'ADN950101AB1', 'Av. Lázaro Cárdenas', '1500', null, 'Del Sur', 'Guadalajara', 'MX_JAL', '44900', 'CREDITO_30'),
	('PR-TUB', 'Tubería y Conexiones de Occidente', 'TCO080215CD2', 'Calzada Independencia', '820', '4', 'Centro', 'Guadalajara', 'MX_JAL', '44100', 'CONTADO'),
	('PR-ELC', 'Eléctrica Monterra, S. de R.L.', 'EMO110920EF3', 'Av. Constitución', '215', null, 'Centro', 'Monterrey', 'MX_NLE', '64000', 'CREDITO_60'),
	('PR-TOR', 'Tornillería Industrial del Bajío', 'TIB160404GH4', 'Blvd. Aeropuerto', '540', 'B', 'Las Palmas', 'León', 'MX_GUA', '37545', 'CREDITO_30'),
	('PR-PIN', 'Pinturas y Recubrimientos Azteca', 'PRA020718IJ5', 'Eje Central', '975', null, 'Doctores', 'Ciudad de México', 'MX_CMX', '06720', 'CONTADO')
) as v(code, name, tax_id, street, exterior, interior, neighborhood, city, state, postal, terms)
on conflict do nothing;

insert into supplier_contacts (id, supplier_id, name, email, phone, active, created_at, updated_at)
select gen_random_uuid(), s.id, v.name, v.email, v.phone, true, now(), now()
from suppliers s
join (values
	('PR-ACN', 'Laura Méndez Ruiz', 'laura.mendez@acerosdelnorte.mx', '3338124455'),
	('PR-ACN', 'Jorge Ávila', 'jorge.avila@acerosdelnorte.mx', '3338124460'),
	('PR-TUB', 'Ramiro Solís', 'ventas@tuberiasoccidente.mx', '3336147788'),
	('PR-ELC', 'Ana Cecilia Treviño', 'ana.trevino@monterra.mx', '8183401122'),
	('PR-TOR', 'Hugo Ramírez', 'hugo.ramirez@tornilleriaib.mx', '4777123390'),
	('PR-PIN', 'Mariana Cortés', 'mariana.cortes@azteca.mx', '5555106677')
) as v(code, name, email, phone) on v.code = s.code
where not exists (select 1 from supplier_contacts c where c.supplier_id = s.id and c.name = v.name);

-- Los clientes: constructoras, ferreterías y talleres de distintas ciudades.
insert into customers (id, code, name, tax_id, street, exterior_number, interior_number, neighborhood, city, state_id, postal_code, country_id, payment_terms_id, active, created_at, updated_at)
select gen_random_uuid(), v.code, v.name, v.tax_id, v.street, v.exterior, v.interior, v.neighborhood, v.city,
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'STATE' and i.code = v.state),
	v.postal,
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'COUNTRY' and i.code = 'MX'),
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'PAYMENT_TERMS' and i.code = v.terms),
	true, now(), now()
from (values
	('CL-CON', 'Constructora Vela S.A. de C.V.', 'CVE070312KL6', 'Av. Vallarta', '1300', null, 'Americana', 'Guadalajara', 'MX_JAL', '44160', 'CREDITO_30'),
	('CL-FER', 'Ferretería El Tornillo Feliz', 'FTF180622MN7', 'Calle Morelos', '45', null, 'Centro', 'Tlaquepaque', 'MX_JAL', '45500', 'CONTADO'),
	('CL-ING', 'Ingeniería y Obra Civil del Norte', 'IOC060830OP8', 'Av. Gonzalitos', '2200', '8', 'Mitras Centro', 'Monterrey', 'MX_NLE', '64460', 'CREDITO_60'),
	('CL-TAL', 'Talleres Mecánicos Zafiro', 'TMZ140215QR9', 'Av. Tecnológico', '780', null, 'Centro', 'Querétaro', 'MX_QUE', '76000', 'CONTADO'),
	('CL-HOT', 'Hotelera Costa Alegre, S.A.', 'HCA990430ST1', 'Blvd. Francisco Medina Ascencio', '2450', null, 'Zona Hotelera', 'Puerto Vallarta', 'MX_JAL', '48333', 'CREDITO_30')
) as v(code, name, tax_id, street, exterior, interior, neighborhood, city, state, postal, terms)
on conflict do nothing;

insert into customer_contacts (id, customer_id, name, email, phone, active, created_at, updated_at)
select gen_random_uuid(), c.id, v.name, v.email, v.phone, true, now(), now()
from customers c
join (values
	('CL-CON', 'Ing. Alberto Vela', 'compras@constructoravela.mx', '3331229080'),
	('CL-CON', 'Karla Domínguez', 'karla.dominguez@constructoravela.mx', '3331229084'),
	('CL-FER', 'Don Ramiro Ochoa', 'eltornillofeliz@gmail.com', '3336354411'),
	('CL-ING', 'Lic. Sofía Garza', 'sofia.garza@iocnorte.mx', '8181235500'),
	('CL-TAL', 'Marco Zafiro', 'tallerzafiro@hotmail.com', '4422187766'),
	('CL-HOT', 'Paola Ruiz', 'compras@costaalegre.mx', '3222261100')
) as v(code, name, email, phone) on v.code = c.code
where not exists (select 1 from customer_contacts x where x.customer_id = c.id and x.name = v.name);

-- Los materiales de la ferretería, con su código de barras y su mínimo en el almacén.
insert into products (id, code, name, unit_id, barcode, active, minimum, created_at, updated_at)
select gen_random_uuid(), v.code, v.name,
	(select i.id from catalog_item i join catalog c on c.id = i.catalog_id where c.code = 'UNIT_OF_MEASURE' and i.code = 'PIECE'),
	v.barcode, true, v.minimum, now(), now()
from (values
	('MAT-TUB-012', 'Tubo PVC hidráulico 1/2" x 3 m', '7501234500017', 40),
	('MAT-TUB-019', 'Tubo PVC hidráulico 3/4" x 3 m', '7501234500024', 30),
	('MAT-COD-012', 'Codo galvanizado 1/2"', '7501234500031', 100),
	('MAT-COD-019', 'Codo galvanizado 3/4"', '7501234500048', 80),
	('MAT-TEE-012', 'Tee galvanizada 1/2"', '7501234500055', 60),
	('MAT-TOR-014', 'Tornillo hexagonal 1/4" x 1"', '7501234500062', 500),
	('MAT-TOR-038', 'Tornillo hexagonal 3/8" x 2"', '7501234500079', 300),
	('MAT-TUE-014', 'Tuerca hexagonal 1/4"', '7501234500086', 500),
	('MAT-RON-014', 'Rondana plana 1/4"', '7501234500093', 800),
	('MAT-CAB-014', 'Cable THW calibre 14 AWG (metro)', '7501234500109', 200),
	('MAT-CAB-012', 'Cable THW calibre 12 AWG (metro)', '7501234500116', 200),
	('MAT-APA-015', 'Apagador de pared sencillo', '7501234500123', 50),
	('MAT-CON-015', 'Contacto dúplex 15 A', '7501234500130', 50),
	('MAT-PIN-BLA', 'Pintura vinílica blanca 19 L', '7501234500147', 12),
	('MAT-PIN-ROJ', 'Pintura anticorrosiva roja 4 L', '7501234500154', 15),
	('MAT-CEM-050', 'Cemento gris 50 kg', '7501234500161', 60),
	('MAT-CAL-025', 'Cal hidratada 25 kg', '7501234500178', 30),
	('MAT-LIJ-100', 'Lija para metal grano 100', '7501234500185', 100)
) as v(code, name, barcode, minimum)
on conflict do nothing;

-- Los almacenes: el central y dos sucursales, cada uno con sus racks y niveles.
insert into warehouses (id, code, name, active, created_at, updated_at)
select gen_random_uuid(), v.code, v.name, true, now(), now()
from (values
	('ALM-CEN', 'Almacén Central Guadalajara'),
	('ALM-NOR', 'Sucursal Monterrey Norte'),
	('ALM-SUR', 'Sucursal Mérida Sur')
) as v(code, name)
on conflict do nothing;

-- Una ubicación es pasillo + rack + nivel, con el nivel en letra: A01A, A01B, B02A… (locations_code_format).
insert into locations (id, warehouse_id, code, aisle, rack, level, temporary, active, created_at, updated_at)
select gen_random_uuid(), w.id, a.aisle || a.rack || n.level, a.aisle, a.rack, n.level, false, true, now(), now()
from warehouses w
join (values ('A', '01'), ('A', '02'), ('A', '03'), ('B', '01'), ('B', '02'), ('C', '01')) as a(aisle, rack) on true
join (values ('A'), ('B')) as n(level) on true
where w.code in ('ALM-CEN', 'ALM-NOR', 'ALM-SUR')
on conflict do nothing;
