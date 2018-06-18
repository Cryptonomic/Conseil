--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.13
-- Dumped by pg_dump version 9.5.13

SET statement_timeout = 0;
SET lock_timeout = 0;
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
    balance numeric NOT NULL
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
    operations_hash character varying
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
    kind character varying NOT NULL,
    block character varying,
    level integer,
    slots character varying,
    nonce character varying,
    pkh character varying,
    secret character varying,
    source character varying,
    counter integer,
    public_key character varying,
    amount character varying,
    destination character varying,
    manager_pub_key character varying,
    balance character varying,
    spendable boolean,
    delegatable boolean,
    delegate character varying,
    operation_group_hash character varying NOT NULL,
    operation_id integer NOT NULL,
    fee character varying,
    storage_limit character varying,
    gas_limit character varying
);


--
-- Name: operations_operation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.operations_operation_id_seq
    START WITH 177489
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: operations_operation_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.operations_operation_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: operations_operation_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.operations_operation_id_seq1 OWNED BY public.operations.operation_id;


--
-- Name: operation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations ALTER COLUMN operation_id SET DEFAULT nextval('public.operations_operation_id_seq1'::regclass);


--
-- Name: OperationGroups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT "OperationGroups_pkey" PRIMARY KEY (hash);


--
-- Name: accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id, block_id);


--
-- Name: blocks_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_hash_key UNIQUE (hash);


--
-- Name: fki_block; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_block ON public.operation_groups USING btree (block_id);


--
-- Name: accounts_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- Name: block; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT block FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- Name: blocks_predecessor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_predecessor_fkey FOREIGN KEY (predecessor) REFERENCES public.blocks(hash);


--
-- Name: fk_opgroups; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_opgroups FOREIGN KEY (operation_group_hash) REFERENCES public.operation_groups(hash);


--
-- PostgreSQL database dump complete
--

