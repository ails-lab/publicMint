--helper functions

CREATE OR REPLACE FUNCTION execute(TEXT) RETURNS VOID AS $$

BEGIN EXECUTE $1; END;

$$ LANGUAGE plpgsql STRICT;

CREATE OR REPLACE FUNCTION schema_exists(TEXT) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM information_schema.schemata WHERE schema_name = $1);

$$ language sql STRICT;

CREATE OR REPLACE FUNCTION table_exists(TEXT) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM information_schema.tables WHERE (table_schema, table_name, table_type) = (current_schema(), $1, 'BASE TABLE'));

$$ language sql STRICT;

CREATE OR REPLACE FUNCTION view_exists( TEXT) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM information_schema.views WHERE (table_schema, table_name) = (current_schema(),$1));

$$ language sql STRICT;

CREATE OR REPLACE FUNCTION column_exists(TEXT, TEXT) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM information_schema.columns WHERE (table_schema, table_name, column_name) = (current_schema(), $1, $2));

$$ language sql STRICT;


CREATE OR REPLACE FUNCTION index_exists(TEXT, TEXT) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM pg_catalog.pg_indexes  WHERE (schemaname, tablename, indexname) = (current_schema(), $1, $2));

$$ language sql STRICT;

CREATE OR REPLACE FUNCTION sequence_exists( TEXT ) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM information_schema.sequences WHERE (sequence_schema, sequence_name ) = (current_schema(), $1 ));

$$ language sql STRICT;

CREATE OR REPLACE FUNCTION foreign_key_exists( TEXT, TEXT ) RETURNS bool as $$

    SELECT exists(SELECT 1 FROM information_schema.key_column_usage WHERE (table_schema, table_name, column_name ) = (current_schema(), $1, $2));

$$ language sql STRICT;
-- end helpers




-- add the publish_allowed column to organization
select execute( $$alter table organization add column publish_allowed boolean not null default FALSE$$)
 where NOT column_exists( 'organization', 'publish_allowed');

-- add a crosswalk to transformation
select execute( $$alter table transformation add column crosswalk_id int references crosswalk$$)
 where NOT column_exists( 'transformation', 'crosswalk_id');


 -- adding xsl support to mappings
 select execute( $$alter table mapping add column xsl text $$)
 where NOT column_exists( 'mapping', 'xsl');

 -- sequence for key-value store feature
 select execute( $$create sequence seq_meta_id start with 1000$$ )
 where NOT sequence_exists( 'seq_meta_id');


 -- new publication moves from publication tables to
 -- status on initial dataset.

 --  change again, this cannot stay, it conflicts with new change
 -- select execute( $$alter table dataset add column publication_status text $$)
 -- where NOT column_exists( 'dataset', 'publication_status');

 -- update dataset set publication_status='NOT APPLICABLE' where publication_status is null;


 -- select execute( $$alter table dataset add column publish_date timestamp $$)
 -- where NOT column_exists( 'dataset', 'publish_date');

 -- select execute( $$ drop table publication $$ )
 -- where table_exists( 'publication' );

 -- select execute( $$ drop table publication_input $$ )
 -- where table_exists('publication_input' );


 -- start creating valid_item_counts if they dont exist in the database
 select execute ($$
	 create table tmp_dataset_valid as
       select ds.dataset_id, count(*) as valid_item_count
       from dataset ds, item i
       where i.dataset_id = ds.dataset_id
       and i.valid= true
       group by ds.dataset_id
$$ )
 where NOT column_exists( 'dataset', 'valid_item_count');

select execute( $$alter table dataset add column valid_item_count int $$)
 where NOT column_exists( 'dataset', 'valid_item_count');


select execute( $$
  update dataset ds set valid_item_count = (
	select valid_item_count
	from tmp_dataset_valid tdv
	where tdv.dataset_id = ds.dataset_id )
$$ )
where table_exists( 'tmp_dataset_valid' );

select execute( $$
  update dataset ds set valid_item_count = 0 where valid_item_count is null
$$ )
where table_exists( 'tmp_dataset_valid' );


select execute( $$ drop table tmp_dataset_valid $$)
where table_exists( 'tmp_dataset_valid' );

-- end creating valid_item_counts

-- select execute( $$ alter table dataset add column published_item_count int NOT NULL default 0 $$ )
-- where NOT column_exists( 'dataset', 'published_item_count');

-- xpath value index
select execute( $$ create index idx_xpath_stats_values_summary on xpath_stats_values( xpath_summary_id, start ) $$ )
where NOT index_exists( 'xpath_stats_values', 'idx_xpath_stats_values_summary');

-- value edits table
select execute( $$ create sequence seq_value_edit_id start with 1000 $$ )
where not sequence_exists( 'seq_value_edit_id');

select execute( $$ create table value_edit (
	value_edit_id bigint primary key not null,
	dataset_id int,
	xpath_holder_id int,
	match_string text,
	replace_string text,
	created timestamp
) $$ )
where not table_exists( 'value_edit');

select execute( $$ alter table mapping add column last_modified timestamp $$ )
where not column_exists( 'mapping', 'last_modified');

update mapping set last_modified=creation_date where last_modified is null;

select execute( $$ alter table xml_schema add column last_modified timestamp $$ )
where not column_exists( 'xml_schema', 'last_modified');

update xml_schema set last_modified=created where last_modified is null;

select execute( $$ alter table xml_schema add column json_original text $$ )
where not column_exists( 'xml_schema', 'json_original');

select execute( $$ alter table xml_schema add column schematron_rules text $$ )
where not column_exists( 'xml_schema', 'schematron_rules');

select execute( $$ alter table xml_schema add column schematron_xsl text $$ )
where not column_exists( 'xml_schema', 'schematron_xsl');

select execute( $$ alter table organization add column json_folders text $$ )
where not column_exists( 'organization', 'json_folders');

-- json_folders migrate to dataset
-- select execute( $$ alter table data_upload add column json_folders text $$ )
-- where not column_exists( 'data_upload', 'json_folders');

-- some foreign key constraints are missing --
-- on dataset organization and users --
-- on dataset_log on users --
-- on xpath_stats_values on dataset_id and xpath_summary_id --
-- problem, existing data might break those, have to delete illegal records --



-- remove datasets that don't match with orgs
select execute( $$ delete from dataset where dataset_id in (
			select ds.dataset_id
			from dataset ds
			left join organization o on ds.organization_id = o.organization_id
			where o.organization_id is null ) $$ )
where not foreign_key_exists( 'dataset', 'organization_id');

-- remove datasets that don't match with users
select execute( $$ delete from dataset where dataset_id in (
			select ds.dataset_id
			from dataset ds
			left join users u on ds.creator_id = u.users_id
			where  u.users_id is null ) $$ )
where not foreign_key_exists( 'dataset', 'creator_id');

-- put the foreign keys
select execute( $$ alter table dataset add foreign key (organization_id ) references organization $$ )
where not foreign_key_exists( 'dataset', 'organization_id');

select execute( $$ alter table dataset add foreign key (creator_id ) references users $$ )
where not foreign_key_exists( 'dataset', 'creator_id');

-- dataset log cleanup that doesnt have users -

select execute( $$ delete from dataset_log where dataset_log_id in (
	select dl.dataset_log_id
	from dataset_log dl
	left join dataset ds on ds.dataset_id = dl.dataset_id
	where ds.dataset_id is null) $$ )
where not foreign_key_exists( 'dataset_log', 'dataset_id');

select execute( $$ alter table dataset_log add foreign key( dataset_id ) references dataset on delete cascade $$ )
where not foreign_key_exists( 'dataset_log', 'dataset_id');

-- xpath_stats_values to dataset_id  --
select execute( $$ create index idx_xpath_stats_values_dataset on xpath_stats_values( dataset_id ) $$)
where NOT index_exists( 'xpath_stats_values', 'idx_xpath_stats_values_dataset');

select execute( $$ delete from xpath_stats_values where xpath_stats_values_id in (
	select xsv.xpath_stats_values_id
	from xpath_stats_values xsv
	left join dataset ds on ds.dataset_id = xsv.dataset_id
	where ds.dataset_id is null) $$ )
where not foreign_key_exists( 'xpath_stats_values', 'dataset_id');

select execute( $$ alter table xpath_stats_values add foreign key( dataset_id ) references dataset on delete cascade $$ )
where not foreign_key_exists( 'xpath_stats_values', 'dataset_id');


-- broken semantics on data upload delete --
-- if a transformation exists, the part in the transformation table is delete that has the parent id
-- the part in the dataset table is not affected, leaving an illegal dataset in the db

-- delete illegal transformations
delete from dataset where dataset_id in (
	select ds.dataset_id
	from dataset ds
	left join transformation tr
		on ds.dataset_id = tr.dataset_id
	where ds.subtype = 'Transformation'
		and tr.dataset_id is null );

-- foreign key in transformation to parent_dataset_id should not cascade delete!!
-- why not ... really puzzling decisions.. if the parent dataset disappers, so should the transformation?
-- or maybe its to disable delete when someone wants to delete the origin of a transformation ..

alter table transformation drop constraint transformation_dataset_id_fkey;
alter table transformation drop constraint transformation_parent_dataset_id_fkey;
alter table transformation add constraint transformation_dataset_id_fkey foreign key ( dataset_id ) references dataset on delete cascade;
alter table transformation add constraint transformation_parent_dataset_id_fkey foreign key ( parent_dataset_id ) references dataset;


-- another try at publication. we now record which datasets are published and when.
-- add publication table

select execute( $$ create sequence seq_publication_record_id start with 1000 $$ )
where not sequence_exists( 'seq_publication_record_id');

select execute( $$ create table publication_record (
	publication_record_id int primary key,
	organization_id int references organization,
	users_id int references users,
	start_date timestamp,
	end_date timestamp,
	original_dataset_id int references dataset,
	published_dataset_id int references dataset,
	report text,
	status text,
	published_item_count int
) $$ )
where not table_exists( 'publication_record');

-- migrate data from dataset table
select execute( $$  insert into publication_record( publication_record_id, organization_id, users_id, start_date, end_date,
			original_dataset_id, published_dataset_id, status, published_item_count )
select nextval('seq_publication_record_id'), ds.organization_id, ds.creator_id, ds.publish_date, ds.publish_date,
			ds.dataset_id, ds.dataset_id, 'OK', ds.valid_item_count
from dataset ds where ds.publication_status = 'OK' $$ )
where column_exists('dataset','publish_date');

-- remove unused columns

select execute( $$ alter table dataset drop column publish_date $$ )
where column_exists( 'dataset', 'publish_date' );
select execute( $$ alter table dataset drop column publication_status $$ )
where column_exists( 'dataset', 'publication_status' );
select execute( $$ alter table dataset drop column published_item_count $$ )
where column_exists( 'dataset', 'published_item_count' );

-- time to clean up with the node index idea

select execute( $$ alter table dataset drop column node_index_status $$ )
where column_exists( 'dataset', 'node_index_status' );
select execute( $$ alter table dataset drop column node_index_name $$ )
where column_exists( 'dataset', 'node_index_name' );
select execute( $$ alter table dataset drop column node_index_size $$ )
where column_exists( 'dataset', 'node_index_size' );

select execute( $$ drop table xml_node_master $$ )
where table_exists( 'xml_node_master' );

select execute( $$ drop sequence seq_xml_node_id $$ )
where sequence_exists( 'seq_xml_node_id');

select execute( $$ alter table item drop column xml_node_id $$ )
where column_exists( 'item', 'xml_node_id' );

drop trigger if exists dataset_delete_trigger on dataset cascade;

-- json folders should be on Dataset, we will have bare datasets for annotation
select execute( $$ alter table dataset add column json_folders text $$ )
where not column_exists( 'dataset', 'json_folders' );

select execute( $$ update dataset ds set json_folders = du.json_folders from data_upload du
                     where du.dataset_id = ds.dataset_id $$ )
where column_exists( 'data_upload', 'json_folders' );

select execute( $$ alter table data_upload drop column json_folders $$ )
where column_exists( 'data_upload', 'json_folders' );

-- end json_folder juggling

-- and while we're on it, the blob_wrap is an upload thing
-- select execute( $$ alter table data_upload add column blob_wrap_id int references blob_wrap $$ )
-- where not column_exists( 'data_upload', 'blob_wrap_id' );

select execute($$
  create table annotated_dataset (
	dataset_id int references dataset on delete cascade,
	parent_dataset_id int references dataset
  )
$$)
where not table_exists('annotated_dataset');

select execute( $$ create table project (
	project_id bigint primary key not null,
	color text,
	name text,
	description text
) $$ )
where not table_exists( 'project');

select execute( $$ create sequence seq_project_id start with 1000 $$ )
where not sequence_exists( 'seq_project_id');

-- some more cleanup
select execute( $$ drop table activity $$ )
where table_exists( 'activity');

-- couple of missing indices
select execute( $$ create index annotated_dataset_dataset_id_idx on annotated_dataset( dataset_id ) $$)
where NOT index_exists( 'annotated_dataset', 'annotated_dataset_dataset_id_idx');

select execute( $$ create index annotated_dataset_parent_dataset_id_idx on annotated_dataset( parent_dataset_id ) $$)
where NOT index_exists( 'annotated_dataset', 'annotated_dataset_parent_dataset_id_idx');

select execute( $$ create index publication_record_published_dataset_id_idx on publication_record( published_dataset_id ) $$)
where NOT index_exists( 'publication_record', 'publication_record_published_dataset_id_idx');

select execute( $$ create index transformation_parent_dataset_id_idx on transformation( parent_dataset_id ) $$)
where NOT index_exists( 'transformation', 'transformation_parent_dataset_id_idx');

select execute( $$ create index transformation_dataset_id_idx on transformation( dataset_id ) $$)
where NOT index_exists( 'transformation', 'transformation_dataset_id_idx');

select execute( $$ create index data_upload_dataset_id_idx on data_upload( dataset_id ) $$)
where NOT index_exists( 'data_upload', 'data_upload_dataset_id_idx');

select execute( $$ alter table dataset add column project_ids smallint[] default '{}' NOT NULL $$)
where not column_exists( 'dataset' , 'project_ids' );
 
select execute( $$ alter table mapping add column project_ids smallint[] default '{}' NOT NULL $$)
where not column_exists( 'mapping' , 'project_ids' );
 
select execute( $$ alter table users add column project_ids smallint[] default '{}' NOT NULL $$)
where not column_exists( 'users' , 'project_ids' );
 
select execute( $$ alter table organization add column project_ids smallint[] default '{}' NOT NULL $$)
where not column_exists( 'organization' , 'project_ids' );

SELECT execute( $$ ALTER TABLE publication_record ADD COLUMN target text $$ )
WHERE NOT column_exists( 'publication_record', 'target');


SELECT execute( $$ CREATE TABLE enrichment (
    enrichment_id int primary key NOT NULL,
    name text,
    headers text,
    creator_id integer references users,
    organization_id integer references organization,
    byte_size integer,
    line_count integer,
    project_ids smallint[],
    gzip_csv bytea,
    creation_date timestamp without time zone,
    last_modified timestamp without time zone,
    CONSTRAINT enrichment_organization_fk FOREIGN KEY (organization_id)
        REFERENCES organization (organization_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID,
    CONSTRAINT enrichment_user_fk FOREIGN KEY (creator_id)
        REFERENCES users (users_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
        NOT VALID

)  $$ )
WHERE NOT table_exists( 'enrichment');

select execute( $$ create sequence seq_enrichment_id start with 1000 $$ )
where not sequence_exists( 'seq_enrichment_id');

select execute( $$ 
	alter table transformation add column enrichment_id bigint REFERENCES enrichment(enrichment_id);
    alter table transformation add column json_enrichment_parameters text;
$$ )  where not column_exists( 'transformation', 'enrichment_id');


SELECT execute( $$ CREATE TABLE translation (
    translation_id int primary key NOT NULL,
    organization_id integer references organization not null,
    creator_id integer references users on delete set null,
    project_ids smallint[] default '{}',
	dataset_id integer references dataset on delete set null,
	original_dataset_id integer references dataset on delete set null,
	
	-- when the process started
	start_date timestamp without time zone,
	-- end when it finished
	end_date timestamp without time zone,
	
	
	-- RUNNING, OK, FAILED
    status text,
    
    -- some representation of the input paramters for this job
    json_config text,
    
    -- on OK how many literals of what language were translated, how many language detected ...etc
    json_stats text
  
)  $$ )
WHERE NOT table_exists( 'translation');

select execute( $$ create sequence seq_translation_id start with 1000 $$ )
where not sequence_exists( 'seq_translation_id');
 

SELECT execute( $$ CREATE TABLE translation_literal (
    translation_literal_id bigint primary key NOT NULL,
    translation_id integer not null references translation on delete cascade,
    
    -- long literals dont index well, lets try a hash index
	-- a hash of the source literal
    hash bigint,
    
    original_literal text,
    translated_literal text,
    
    field_name text,
    -- how frequent was this literal in the original dataset, how important is correct translation
    count integer,
    
    -- what language was this tagged with
    original_language_tag text,
    
    -- what language was assumed on sending for translation
    -- if detection happened, this is the detected language and detection_score >=0
    used_language varchar(2),
    
    -- for the time being we will only translate to english, but who knows,
    -- its cheap to be defensive here.
    target_language varchar(2) default 'en',
    
    -- true for reviewed and correct, false for reviewed and broken, null for not reviewed
    human_review boolean,
    
    -- anumeric value of accuracy provided with this translation
    -- -1 for not available, 0...1000 for range 0-1
    translation_score smallint,
    
    -- if there was detection and if there was a score, its here, same as above
    detection_score smallint
)  $$ )
WHERE NOT table_exists( 'translation_literal');

select execute( $$ create sequence seq_translation_literal_id start with 1000 $$ )
where not sequence_exists( 'seq_translation_literal_id');

select execute( $$ create index translation_literal_translation_id_idx on translation_literal( translation_id, used_language, count ) $$)
where NOT index_exists( 'translation_literal', 'translation_literal_translation_id_idx');

select execute( $$ create index translation_literal_hash_idx on translation_literal using hash( hash ) $$)
where NOT index_exists( 'translation_literal', 'translation_literal_hash_idx');
select execute( $$ 
	alter table transformation add column translation_id int REFERENCES translation;
	alter table transformation rename column json_enrichment_parameters to json_parameters;
$$ )  where not column_exists( 'transformation', 'translation_id');


select execute( $$
    alter table enrichment rename column line_count to record_count;
    alter table enrichment rename column gzip_csv to content;
    alter table enrichment add column format text default 'CSV_GZ';
    alter table enrichment add column json_stats text default '{}';
    alter table enrichment drop column last_modified;
$$ ) where not column_exists( 'enrichment', 'record_count');

select execute( $$
    -- no reference integrity here, not worth it, will likely get deleted anyway when item disappears
    -- since translation might disappear
	alter table translation_literal add column item_id bigint not null default -1;
 $$ ) where not column_exists( 'translation_literal', 'item_id');
