--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.19
-- Dumped by pg_dump version 11.5 (Ubuntu 11.5-3.pgdg18.04+1)

-- Started on 2019-10-11 02:59:07 EDT

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

SET default_tablespace = '';

SET default_with_oids = false;

--
-- TOC entry 181 (class 1259 OID 99594)
-- Name: accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    delegate character varying,
    counter integer,
    script character varying,
    storage character varying,
    balance numeric NOT NULL,
    block_level numeric DEFAULT '-1'::integer NOT NULL,
    manager character varying, -- retro-compat from protocol 5+
    spendable boolean, -- retro-compat from protocol 5+
    delegate_setable boolean, -- retro-compat from protocol 5+
    delegate_value char varying -- retro-compat from protocol 5+
);


--
-- TOC entry 182 (class 1259 OID 99601)
-- Name: accounts_checkpoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts_checkpoint (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL
);


--
-- TOC entry 193 (class 1259 OID 99668)
-- Name: baking_rights; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.baking_rights (
    block_hash character varying NOT NULL,
    level integer NOT NULL,
    delegate character varying NOT NULL,
    priority integer NOT NULL,
    estimated_time timestamp without time zone NOT NULL
);


--
-- TOC entry 183 (class 1259 OID 99608)
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
    category character varying,
    operation_group_hash character varying
);


--
-- TOC entry 184 (class 1259 OID 99614)
-- Name: balance_updates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.balance_updates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 2245 (class 0 OID 0)
-- Dependencies: 184
-- Name: balance_updates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.balance_updates_id_seq OWNED BY public.balance_updates.id;


--
-- TOC entry 185 (class 1259 OID 99616)
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
    expected_commitment boolean,
    priority integer
);


--
-- TOC entry 186 (class 1259 OID 99628)
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
-- TOC entry 187 (class 1259 OID 99635)
-- Name: delegates_checkpoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delegates_checkpoint (
    delegate_pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL
);


--
-- TOC entry 194 (class 1259 OID 99677)
-- Name: endorsing_rights; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.endorsing_rights (
    block_hash character varying NOT NULL,
    level integer NOT NULL,
    delegate character varying NOT NULL,
    slot integer NOT NULL,
    estimated_time timestamp without time zone NOT NULL
);


--
-- TOC entry 188 (class 1259 OID 99642)
-- Name: fees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fees (
    low integer NOT NULL,
    medium integer NOT NULL,
    high integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    kind character varying NOT NULL,
    cycle integer,
    level integer
);


--
-- TOC entry 189 (class 1259 OID 99648)
-- Name: operation_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operation_groups (
    protocol character varying NOT NULL,
    chain_id character varying,
    hash character varying NOT NULL,
    branch character varying NOT NULL,
    signature character varying,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- TOC entry 190 (class 1259 OID 99654)
-- Name: operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operations (
    branch character varying,
    number_of_slots integer,
    cycle integer,
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
    proposal character varying,
    spendable boolean,
    delegatable boolean,
    script character varying,
    storage character varying,
    status character varying,
    consumed_gas numeric,
    storage_size numeric,
    paid_storage_size_diff numeric,
    originated_contracts character varying,
    block_hash character varying NOT NULL,
    block_level integer NOT NULL,
    ballot character varying,
    internal boolean NOT NULL,
    period integer,
    "timestamp" timestamp without time zone NOT NULL
);


--
-- TOC entry 191 (class 1259 OID 99660)
-- Name: operations_operation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.operations_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 2246 (class 0 OID 0)
-- Dependencies: 191
-- Name: operations_operation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.operations_operation_id_seq OWNED BY public.operations.operation_id;


--
-- TOC entry 192 (class 1259 OID 99662)
-- Name: rolls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rolls (
    pkh character varying NOT NULL,
    rolls integer NOT NULL,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- TOC entry 2077 (class 2604 OID 99686)
-- Name: balance_updates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_updates ALTER COLUMN id SET DEFAULT nextval('public.balance_updates_id_seq'::regclass);


--
-- TOC entry 2080 (class 2604 OID 99687)
-- Name: operations operation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations ALTER COLUMN operation_id SET DEFAULT nextval('public.operations_operation_id_seq'::regclass);


--
-- TOC entry 2095 (class 2606 OID 99689)
-- Name: operation_groups OperationGroups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT "OperationGroups_pkey" PRIMARY KEY (block_id, hash);


--
-- TOC entry 2082 (class 2606 OID 99691)
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);


--
-- TOC entry 2110 (class 2606 OID 99675)
-- Name: baking_rights baking_rights_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.baking_rights
    ADD CONSTRAINT baking_rights_pkey PRIMARY KEY (level, delegate);


--
-- TOC entry 2087 (class 2606 OID 99693)
-- Name: balance_updates balance_updates_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_updates
    ADD CONSTRAINT balance_updates_key PRIMARY KEY (id);


--
-- TOC entry 2089 (class 2606 OID 99695)
-- Name: blocks blocks_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_hash_key UNIQUE (hash);


--
-- TOC entry 2092 (class 2606 OID 99697)
-- Name: delegates delegates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegates
    ADD CONSTRAINT delegates_pkey PRIMARY KEY (pkh);


--
-- TOC entry 2114 (class 2606 OID 99684)
-- Name: endorsing_rights endorsing_rights_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endorsing_rights
    ADD CONSTRAINT endorsing_rights_pkey PRIMARY KEY (level, delegate, slot);


--
-- TOC entry 2105 (class 2606 OID 99699)
-- Name: operations operationId; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT "operationId" PRIMARY KEY (operation_id);


--
-- TOC entry 2108 (class 1259 OID 99676)
-- Name: baking_rights_level_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX baking_rights_level_idx ON public.baking_rights USING btree (level);


--
-- TOC entry 2112 (class 1259 OID 99685)
-- Name: endorsing_rights_level_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX endorsing_rights_level_idx ON public.endorsing_rights USING btree (level);


--
-- TOC entry 2096 (class 1259 OID 99700)
-- Name: fki_block; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_block ON public.operation_groups USING btree (block_id);


--
-- TOC entry 2111 (class 1259 OID 108189)
-- Name: fki_fk_block_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_fk_block_hash ON public.baking_rights USING btree (block_hash);


--
-- TOC entry 2115 (class 1259 OID 108211)
-- Name: fki_fk_block_hash2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_fk_block_hash2 ON public.endorsing_rights USING btree (block_hash);


--
-- TOC entry 2098 (class 1259 OID 99701)
-- Name: fki_fk_blockhashes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fki_fk_blockhashes ON public.operations USING btree (block_hash);


--
-- TOC entry 2083 (class 1259 OID 99702)
-- Name: ix_accounts_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_block_level ON public.accounts USING btree (block_level);


--
-- TOC entry 2084 (class 1259 OID 99703)
-- Name: ix_accounts_checkpoint_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_account_id ON public.accounts_checkpoint USING btree (account_id);


--
-- TOC entry 2085 (class 1259 OID 99704)
-- Name: ix_accounts_checkpoint_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_block_level ON public.accounts_checkpoint USING btree (block_level);


--
-- TOC entry 2090 (class 1259 OID 99706)
-- Name: ix_blocks_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_blocks_level ON public.blocks USING btree (level);


--
-- TOC entry 2093 (class 1259 OID 99707)
-- Name: ix_delegates_checkpoint_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_delegates_checkpoint_block_level ON public.delegates_checkpoint USING btree (block_level);


--
-- TOC entry 2097 (class 1259 OID 99708)
-- Name: ix_operation_groups_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operation_groups_block_level ON public.operation_groups USING btree (block_level);


--
-- TOC entry 2099 (class 1259 OID 99709)
-- Name: ix_operations_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_block_level ON public.operations USING btree (block_level);


--
-- TOC entry 2100 (class 1259 OID 99713)
-- Name: ix_operations_delegate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_delegate ON public.operations USING btree (delegate);


--
-- TOC entry 2101 (class 1259 OID 99710)
-- Name: ix_operations_destination; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_destination ON public.operations USING btree (destination);


--
-- TOC entry 2102 (class 1259 OID 99711)
-- Name: ix_operations_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_source ON public.operations USING btree (source);


--
-- TOC entry 2103 (class 1259 OID 99712)
-- Name: ix_operations_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_operations_timestamp ON public.operations USING btree ("timestamp");


--
-- TOC entry 2106 (class 1259 OID 99714)
-- Name: ix_rolls_block_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rolls_block_id ON public.rolls USING btree (block_id);


--
-- TOC entry 2107 (class 1259 OID 99715)
-- Name: ix_rolls_block_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rolls_block_level ON public.rolls USING btree (block_level);


--
-- TOC entry 2116 (class 2606 OID 99716)
-- Name: accounts accounts_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2120 (class 2606 OID 99721)
-- Name: operation_groups block; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_groups
    ADD CONSTRAINT block FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2117 (class 2606 OID 99726)
-- Name: accounts_checkpoint checkpoint_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts_checkpoint
    ADD CONSTRAINT checkpoint_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2119 (class 2606 OID 99741)
-- Name: delegates_checkpoint delegate_checkpoint_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegates_checkpoint
    ADD CONSTRAINT delegate_checkpoint_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2118 (class 2606 OID 99746)
-- Name: delegates delegates_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delegates
    ADD CONSTRAINT delegates_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


--
-- TOC entry 2124 (class 2606 OID 108184)
-- Name: baking_rights fk_block_hash; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.baking_rights
    ADD CONSTRAINT fk_block_hash FOREIGN KEY (block_hash) REFERENCES public.blocks(hash) NOT VALID;


--
-- TOC entry 2125 (class 2606 OID 108201)
-- Name: endorsing_rights fk_block_hash; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endorsing_rights
    ADD CONSTRAINT fk_block_hash FOREIGN KEY (block_hash) REFERENCES public.blocks(hash) NOT VALID;


--
-- TOC entry 2121 (class 2606 OID 99751)
-- Name: operations fk_blockhashes; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_blockhashes FOREIGN KEY (block_hash) REFERENCES public.blocks(hash);


--
-- TOC entry 2122 (class 2606 OID 99756)
-- Name: operations fk_opgroups; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operations
    ADD CONSTRAINT fk_opgroups FOREIGN KEY (operation_group_hash, block_hash) REFERENCES public.operation_groups(hash, block_id);


--
-- TOC entry 2123 (class 2606 OID 99761)
-- Name: rolls rolls_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rolls
    ADD CONSTRAINT rolls_block_id_fkey FOREIGN KEY (block_id) REFERENCES public.blocks(hash);


-- Completed on 2019-10-11 02:59:07 EDT

--
-- PostgreSQL database dump complete
--

