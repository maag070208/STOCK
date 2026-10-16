-- The design of a label, as the editor holds it (labels.md section 4): the blocks a person put on it, so the screen
-- opens the label they made instead of reading it back out of the ZPL. The ZPL stays what prints; the design is what
-- the designer edits, and a template written by hand simply has none.
alter table label_templates add column if not exists design jsonb;
