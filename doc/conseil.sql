--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.17
-- Dumped by pg_dump version 11.4 (Ubuntu 11.4-1.pgdg18.04+1)

-- Started on 2019-06-27 13:32:57 EDT

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 196 (class 1255 OID 7467460)
-- Name: truncate_tables(character varying); Type: FUNCTION; Schema: public; Owner: -
--

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


SET default_with_oids = false;

--
-- TOC entry 181 (class 1259 OID 7467461)
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
    storage character varying,
    balance numeric NOT NULL,
    block_level numeric DEFAULT '-1'::integer NOT NULL
);


--
-- TOC entry 189 (class 1259 OID 7467513)
-- Name: accounts_checkpoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts_checkpoint (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL
);


--
-- TOC entry 186 (class 1259 OID 7467492)
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
-- TOC entry 195 (class 1259 OID 7467548)
-- Name: balance_updates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.balance_updates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 2211 (class 0 OID 0)
-- Dependencies: 195
-- Name: balance_updates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.balance_updates_id_seq OWNED BY public.balance_updates.id;


--
-- TOC entry 193 (class 1259 OID 7467539)
-- Name: ballots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ballots (
    pkh character varying NOT NULL,
    ballot character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- TOC entry 182 (class 1259 OID 7467468)
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
-- TOC entry 188 (class 1259 OID 7467507)
-- Name: delegated_contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delegated_contracts (
    account_id character varying NOT NULL,
    delegate_value character varying
);


--
-- TOC entry 187 (class 1259 OID 7467498)
-- Name: delegates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delegates (
    pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    balance numeric,
    frozen_balance numeric,
    staking_balance numeric,
    delegated_balance numeric,
    deactivated boolean NOT NULL,
    grace_period integer NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL
);


--
-- TOC entry 190 (class 1259 OID 7467520)
-- Name: delegates_checkpoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delegates_checkpoint (
    delegate_pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL
);


--
-- TOC entry 183 (class 1259 OID 7467474)
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
-- TOC entry 184 (class 1259 OID 7467480)
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
-- TOC entry 185 (class 1259 OID 7467486)
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
    origination_contracts character varying,
    block_hash character varying NOT NULL,
    block_level integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL
);


--
-- TOC entry 194 (class 1259 OID 7467545)
-- Name: operations_operation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.operations_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 2212 (class 0 OID 0)
-- Dependencies: 194
-- Name: operations_operation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.operations_operation_id_seq OWNED BY public.operations.operation_id;


--
-- TOC entry 191 (class 1259 OID 7467527)
-- Name: proposals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proposals (
    protocol_hash character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- TOC entry 192 (class 1259 OID 7467533)
-- Name: rolls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rolls (
    pkh character varying NOT NULL,
    rolls integer NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- TOC entry 2051 (class 2604 OID 7467550)
-- Name: balance_updates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_updates ALTER COLUMN id SET DEFAULT nextval('public.balance_updates_id_seq'::regclass);


--
-- TOC entry 2050 (class 2604 OID 7467547)
-- Name: operations operation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations ALTER COLUMN operation_id SET DEFAULT nextval('public.operations_operation_id_seq'::regclass);


--
-- TOC entry 2063 (class 2606 OID 7467552)
-- Name: operation_groups OperationGroups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT "OperationGroups_pkey" PRIMARY KEY (hash);


--
-- TOC entry 2056 (class 2606 OID 7467554)
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);


--
-- TOC entry 2073 (class 2606 OID 7467560)
-- Name: balance_updates balance_updates_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_updates
    ADD CONSTRAINT balance_updates_key PRIMARY KEY (id);


--
-- TOC entry 2060 (class 2606 OID 7467556)
-- Name: blocks blocks_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_hash_key UNIQUE (hash);


--
-- TOC entry 2075 (class 2606 OID 7467506)
-- Name: delegates delegates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegates
    ADD CONSTRAINT delegates_pkey PRIMARY KEY (pkh);


--
-- TOC entry 2071 (class 2606 OID 7467558)
-- Name: operations operationId; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT "operationId" PRIMARY KEY (operation_id);


--
-- TOC entry 2064 (class 1259 OID 7467561)
-- Name: fki_block; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_block ON public.operation_groups USING btree (block_id);


--
-- TOC entry 2065 (class 1259 OID 7467562)
-- Name: fki_fk_blockhashes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_fk_blockhashes ON public.operations USING btree (block_hash);


--
-- TOC entry 2057 (class 1259 OID 7467563)
-- Name: ix_accounts_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_block_level ON public.accounts USING btree (block_level);


--
-- TOC entry 2076 (class 1259 OID 7499961)
-- Name: ix_accounts_checkpoint_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_account_id ON public.accounts_checkpoint USING btree (account_id);


--
-- TOC entry 2077 (class 1259 OID 7467568)
-- Name: ix_accounts_checkpoint_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_block_level ON public.accounts_checkpoint USING btree (block_level);


--
-- TOC entry 2058 (class 1259 OID 7467564)
-- Name: ix_accounts_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_manager ON public.accounts USING btree (manager);


--
-- TOC entry 2061 (class 1259 OID 7467565)
-- Name: ix_blocks_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_blocks_level ON public.blocks USING btree (level);


--
-- TOC entry 2078 (class 1259 OID 7467569)
-- Name: ix_delegates_checkpoint_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_delegates_checkpoint_block_level ON public.delegates_checkpoint USING btree (block_level);


--
-- TOC entry 2066 (class 1259 OID 7551880)
-- Name: ix_operations_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_block_level ON public.operations USING btree (block_level);


--
-- TOC entry 2067 (class 1259 OID 7467566)
-- Name: ix_operations_destination; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_destination ON public.operations USING btree (destination);


--
-- TOC entry 2068 (class 1259 OID 7467567)
-- Name: ix_operations_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_source ON public.operations USING btree (source);


--
-- TOC entry 2069 (class 1259 OID 7552181)
-- Name: ix_operations_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_timestamp ON public.operations USING btree ("timestamp");


--
-- TOC entry 2079 (class 1259 OID 7467570)
-- Name: ix_proposals_protocol; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_proposals_protocol ON public.proposals USING btree (protocol_hash);


--
-- TOC entry 2080 (class 2606 OID 7467571)
-- Name: accounts accounts_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2091 (class 2606 OID 7467611)
-- Name: ballots ballot_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ballots
    ADD CONSTRAINT ballot_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2081 (class 2606 OID 7467616)
-- Name: operation_groups block; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT block FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2087 (class 2606 OID 7467591)
-- Name: accounts_checkpoint checkpoint_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts_checkpoint
    ADD CONSTRAINT checkpoint_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2086 (class 2606 OID 7467581)
-- Name: delegated_contracts contracts_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegated_contracts
    ADD CONSTRAINT contracts_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(account_id);


--
-- TOC entry 2085 (class 2606 OID 7467576)
-- Name: delegated_contracts contracts_delegate_pkh_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegated_contracts
    ADD CONSTRAINT contracts_delegate_pkh_fkey FOREIGN KEY (delegate_value) REFERENCES public.delegates(pkh);


--
-- TOC entry 2088 (class 2606 OID 7467596)
-- Name: delegates_checkpoint delegate_checkpoint_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegates_checkpoint
    ADD CONSTRAINT delegate_checkpoint_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2084 (class 2606 OID 7467586)
-- Name: delegates delegates_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegates
    ADD CONSTRAINT delegates_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2082 (class 2606 OID 7467621)
-- Name: operations fk_blockhashes; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_blockhashes FOREIGN KEY (block_hash) REFERENCES public.blocks(hash);


--
-- TOC entry 2083 (class 2606 OID 7467626)
-- Name: operations fk_opgroups; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_opgroups FOREIGN KEY (operation_group_hash) REFERENCES public.operation_groups(hash);


--
-- TOC entry 2089 (class 2606 OID 7467601)
-- Name: proposals proposal_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proposals
    ADD CONSTRAINT proposal_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2090 (class 2606 OID 7467606)
-- Name: rolls rolls_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rolls
    ADD CONSTRAINT rolls_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


-- Completed on 2019-06-27 13:33:00 EDT

--
-- PostgreSQL database dump complete
--

