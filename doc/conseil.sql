--
-- PostgreSQL database dump
--

-- Dumped from database version 10.3
-- Dumped by pg_dump version 10.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: truncate_tables(character varying); Type: FUNCTION; Schema: public; Owner: -
--

DROP FUNCTION IF EXISTS public.truncate_tables(username character varying);

CREATE FUNCTION public.truncate_tables(username character varying) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    statements CURSOR FOR
        SELECT tablename FROM pg_tables
        WHERE tableowner = username AND schemaname = 'public';
BEGIN
    FOR stmt IN statements LOOP
        EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
    END LOOP;
END;
$$;


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    manager character varying NOT NULL,
    spendable boolean NOT NULL,
    delegate_setable boolean NOT NULL,
    delegate_value character varying,
    counter integer NOT NULL,
    script character varying,
    balance numeric NOT NULL,
    block_level numeric DEFAULT '-1'::integer NOT NULL
);


--
-- Name: blocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blocks (
    level integer NOT NULL,
    proto integer NOT NULL,
    predecessor character varying NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    validation_pass integer NOT NULL,
    fitness character varying NOT NULL,
    context character varying,
    signature character varying,
    protocol character varying NOT NULL,
    chain_id character varying,
    hash character varying NOT NULL,
    operations_hash character varying,
    period_kind character varying,
    current_expected_quorum integer,
    active_proposal character varying,
    baker character varying,
    nonce_hash character varying,
    consumed_gas numeric,
    meta_level integer,
    meta_level_position integer,
    meta_cycle integer,
    meta_cycle_position integer,
    meta_voting_period integer,
    meta_voting_period_position integer,
    expected_commitment boolean
);


--
-- Name: fees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fees (
    low integer NOT NULL,
    medium integer NOT NULL,
    high integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    kind character varying NOT NULL
);


--
-- Name: operation_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operation_groups (
    protocol character varying NOT NULL,
    chain_id character varying,
    hash character varying NOT NULL,
    branch character varying NOT NULL,
    signature character varying,
    block_id character varying NOT NULL
);


--
-- Name: operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operations (
    operation_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    kind character varying NOT NULL,
    level integer,
    delegate character varying,
    slots character varying,
    nonce character varying,
    pkh character varying,
    secret character varying,
    source character varying,
    fee numeric,
    counter numeric,
    gas_limit numeric,
    storage_limit numeric,
    public_key character varying,
    amount numeric,
    destination character varying,
    parameters character varying,
    manager_pubkey character varying,
    balance numeric,
    spendable boolean,
    delegatable boolean,
    script character varying,
    storage character varying,
    status character varying,
    consumed_gas numeric,
    storage_size numeric,
    paid_storage_size_diff numeric,
    block_hash character varying NOT NULL,
    block_level integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL
);


--
-- Name: balance_updates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.balance_updates (
    id integer NOT NULL,
    source character varying NOT NULL,
    source_id integer,
    source_hash character varying,
    kind character varying NOT NULL,
    contract character varying,
    change numeric NOT NULL,
    level numeric,
    delegate character varying,
    category character varying
);


--
-- Name: accounts_checkpoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts_checkpoint (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL
);


--
-- Name: proposals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proposals (
    protocol_hash character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- Name: bakers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bakers (
    pkh character varying NOT NULL,
    rolls integer NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- Name: ballots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ballots (
    pkh character varying NOT NULL,
    ballot character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- Name: operations_operation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.operations_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: operations_operation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.operations_operation_id_seq OWNED BY public.operations.operation_id;


--
-- Name: operations operation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations ALTER COLUMN operation_id SET DEFAULT nextval('public.operations_operation_id_seq'::regclass);


--
-- Name: balance_updates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.balance_updates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: balance_updates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.balance_updates_id_seq OWNED BY public.balance_updates.id;


--
-- Name: balance_updates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_updates ALTER COLUMN id SET DEFAULT nextval('public.balance_updates_id_seq'::regclass);


--
-- Name: operation_groups OperationGroups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT "OperationGroups_pkey" PRIMARY KEY (hash);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);


--
-- Name: blocks blocks_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_hash_key UNIQUE (hash);


--
-- Name: operations operationId; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT "operationId" PRIMARY KEY (operation_id);


--
-- Name: balance_updates balance_updates_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_updates
    ADD CONSTRAINT "balance_updates_key" PRIMARY KEY (id);


--
-- Name: fki_block; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_block ON public.operation_groups USING btree (block_id);


--
-- Name: fki_fk_blockhashes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_fk_blockhashes ON public.operations USING btree (block_hash);

--
-- Name: ix_accounts_block_level; Type: INDEX; Schema: public; Owner: -
--
CREATE INDEX ix_accounts_block_level ON public.accounts USING btree (block_level);

--
-- Name: ix_accounts_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_manager ON public.accounts USING btree (manager);


--
-- Name: ix_blocks_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_blocks_level ON public.blocks USING btree (level);


--
-- Name: ix_operations_destination; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_destination ON public.operations USING btree (destination);


--
-- Name: ix_operations_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_source ON public.operations USING btree (source);

--
-- Name: ix_accounts_checkpoint_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_block_level ON public.accounts_checkpoint USING btree (block_level);

--
-- Name: ix_proposals_protocol; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_proposals_protocol ON public.proposals USING btree (protocol_hash);


--
-- Name: accounts accounts_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);

--
-- Name: accounts_checkpoint checkpoint_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts_checkpoint
    ADD CONSTRAINT checkpoint_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- Name: proposals proposal_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proposals
    ADD CONSTRAINT proposal_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);



--
-- Name: bakers baker_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bakers
    ADD CONSTRAINT baker_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);




--
-- Name: ballots ballot_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ballots
    ADD CONSTRAINT ballot_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- Name: operation_groups block; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT block FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- Name: operations fk_blockhashes; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_blockhashes FOREIGN KEY (block_hash) REFERENCES public.blocks(hash);


--
-- Name: operations fk_opgroups; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_opgroups FOREIGN KEY (operation_group_hash) REFERENCES public.operation_groups(hash);


--
-- PostgreSQL database dump complete
--
