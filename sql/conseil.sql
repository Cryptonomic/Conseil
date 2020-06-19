--
-- PostgreSQL database dump
--

-- Dumped from database version 11.4
-- Dumped by pg_dump version 11.4

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
-- Name: tezos; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA tezos;


--
-- Name: truncate_tables(character varying); Type: FUNCTION; Schema: tezos; Owner: -
--

CREATE FUNCTION tezos.truncate_tables(username character varying) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    statements CURSOR FOR
        SELECT tablename FROM pg_tables
        WHERE tableowner = username AND schemaname = 'tezos';
BEGIN
    FOR stmt IN statements LOOP
        EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
    END LOOP;
END;
$$;


SET default_tablespace = '';

SET default_with_oids = false;



CREATE TABLE tezos.baker_registry (
    name character varying NOT NULL,
    is_accepting_delegation boolean,
    external_data_url character varying,
    split numeric,
    payment_accounts character varying,
    minimum_delegation integer,
    payout_delay integer,
    payout_frequency integer,
    minimum_payout integer,
    is_cheap boolean,
    pay_for_own_blocks boolean,
    pay_for_endorsements boolean,
    pay_gained_fees boolean,
    pay_for_accusation_gains boolean,
    subtract_lost_deposits_when_accused boolean,
    subtract_lost_rewards_when_accused boolean,
    subtract_lost_fees_when_accused boolean,
    pay_for_revelation boolean,
    subtract_lost_rewards_when_miss_revelation boolean,
    subtract_lost_fees_when_miss_revelation boolean,
    compensate_missed_blocks boolean,
    pay_for_stolen_blocks boolean,
    compensate_missed_endorsements boolean,
    compensate_low_priority_endorsement_loss boolean,
    overdelegation_threshold integer,
    subtract_rewards_from_uninvited_delegation boolean,
    record_manager character varying,
    "timestamp" timestamp without time zone NOT NULL
);

CREATE TABLE tezos.known_addresses (
    address character varying NOT NULL,
    alias character varying NOT NULL
);

CREATE TABLE tezos.governance (
    voting_period integer NOT NULL,
    voting_period_kind character varying NOT NULL,
    cycle integer,
    level integer,
    block_hash character varying NOT NULL,
    proposal_hash character varying NOT NULL,
    yay_count integer,
    nay_count integer,
    pass_count integer,
    yay_rolls numeric,
    nay_rolls numeric,
    pass_rolls numeric,
    total_rolls numeric,
    block_yay_count integer,
    block_nay_count integer,
    block_pass_count integer,
    block_yay_rolls numeric,
    block_nay_rolls numeric,
    block_pass_rolls numeric,
    PRIMARY KEY (block_hash, proposal_hash, voting_period_kind)
);

CREATE INDEX governance_block_hash_idx ON tezos.governance USING btree (block_hash);
CREATE INDEX governance_proposal_hash_idx ON tezos.governance USING btree (proposal_hash);



CREATE TABLE tezos.processed_chain_events (
    event_level numeric,
    event_type char varying,
    PRIMARY KEY (event_level, event_type)
);

--
-- Name: registered_tokens; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.registered_tokens (
    id integer PRIMARY KEY,
    name text NOT NULL,
    contract_type text NOT NULL,
    account_id text NOT NULL,
    scale integer NOT NULL
);

CREATE TABLE tezos.token_balances (
    token_id integer,
    address text NOT NULL,
    balance numeric NOT NULL,
    block_id character varying NOT NULL,
    block_level numeric DEFAULT '-1'::integer NOT NULL,
    asof timestamp without time zone NOT NULL,
    PRIMARY KEY (token_id, address, block_level)
);

CREATE TABLE tezos.tezos_names (
    name character varying PRIMARY KEY,
    owner character varying,
    resolver character varying,
    registered_at timestamp,
    registration_period integer,
    modified boolean
);

CREATE INDEX tezos_names_resolver_idx ON tezos.tezos_names USING btree (resolver);
CREATE INDEX tezos_names_owner_idx ON tezos.tezos_names USING btree (owner);

--
-- Name: accounts; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.accounts (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    counter integer,
    script character varying,
    storage character varying,
    balance numeric NOT NULL,
    block_level numeric DEFAULT '-1'::integer NOT NULL,
    manager character varying, -- retro-compat from protocol 5+
    spendable boolean, -- retro-compat from protocol 5+
    delegate_setable boolean, -- retro-compat from protocol 5+
    delegate_value char varying, -- retro-compat from protocol 5+
    is_baker boolean NOT NULL DEFAULT false,
    is_activated boolean NOT NULL DEFAULT false
);


CREATE TABLE tezos.accounts_history (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    counter integer,
    storage character varying,
    balance numeric NOT NULL,
    block_level numeric DEFAULT '-1'::integer NOT NULL,
    delegate_value char varying, -- retro-compat from protocol 5+
    asof timestamp without time zone NOT NULL,
    is_baker boolean NOT NULL DEFAULT false,
    cycle integer,
    is_activated boolean NOT NULL DEFAULT false,
    is_active_baker boolean
);

CREATE INDEX ix_account_id ON tezos.accounts_history USING btree (account_id);

--
-- Name: accounts_checkpoint; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.accounts_checkpoint (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL,
    asof timestamp with time zone NOT NULL,
    cycle integer
);


--
-- Name: baking_rights; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.baking_rights (
    block_hash character varying,
    level integer NOT NULL,
    delegate character varying NOT NULL,
    priority integer NOT NULL,
    estimated_time timestamp without time zone,
    cycle integer,
    governance_period integer
);


--
-- Name: balance_updates; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.balance_updates (
    id integer NOT NULL,
    source character varying NOT NULL,
    source_id integer,
    source_hash character varying,
    kind character varying NOT NULL,
    account_id character varying NOT NULL,
    change numeric NOT NULL,
    level numeric,
    category character varying,
    operation_group_hash character varying,
    block_id character varying NOT NULL,
    block_level integer NOT NULL,
    cycle integer,
    period integer
);


--
-- Name: balance_updates_id_seq; Type: SEQUENCE; Schema: tezos; Owner: -
--

CREATE SEQUENCE tezos.balance_updates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: balance_updates_id_seq; Type: SEQUENCE OWNED BY; Schema: tezos; Owner: -
--

ALTER SEQUENCE tezos.balance_updates_id_seq OWNED BY tezos.balance_updates.id;


--
-- Name: blocks; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.blocks (
    level integer NOT NULL,
    proto integer NOT NULL,
    predecessor character varying NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
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
    consumed_gas numeric,
    meta_level integer,
    meta_level_position integer,
    meta_cycle integer,
    meta_cycle_position integer,
    meta_voting_period integer,
    meta_voting_period_position integer,
    priority integer,
    utc_year integer NOT NULL,
    utc_month integer NOT NULL,
    utc_day integer NOT NULL,
    utc_time character varying NOT NULL
);


--
-- Name: bakers; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.bakers (
    pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    balance numeric,
    frozen_balance numeric,
    staking_balance numeric,
    delegated_balance numeric,
    rolls integer DEFAULT 0 NOT NULL,
    deactivated boolean NOT NULL,
    grace_period integer NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL,
    cycle integer,
    period integer
);

--
-- Name: bakers_history; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.bakers_history (
    pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    balance numeric,
    frozen_balance numeric,
    staking_balance numeric,
    delegated_balance numeric,
    rolls integer DEFAULT 0 NOT NULL,
    deactivated boolean NOT NULL,
    grace_period integer NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL,
    cycle integer,
    period integer,
    asof timestamp without time zone NOT NULL
);


--
-- Name: bakers_checkpoint; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.bakers_checkpoint (
    delegate_pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level integer DEFAULT '-1'::integer NOT NULL,
    cycle integer,
    period integer
);


--
-- Name: endorsing_rights; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.endorsing_rights (
    block_hash character varying,
    level integer NOT NULL,
    delegate character varying NOT NULL,
    slot integer NOT NULL,
    estimated_time timestamp without time zone,
    cycle integer,
    governance_period integer,
    endorsed_block integer
);


--
-- Name: fees; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.fees (
    low integer NOT NULL,
    medium integer NOT NULL,
    high integer NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    kind character varying NOT NULL,
    cycle integer,
    level integer
);


--
-- Name: operation_groups; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.operation_groups (
    protocol character varying NOT NULL,
    chain_id character varying,
    hash character varying NOT NULL,
    branch character varying NOT NULL,
    signature character varying,
    block_id character varying NOT NULL,
    block_level integer NOT NULL
);


--
-- Name: operations; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.operations (
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
    parameters_entrypoints character varying,
    parameters_micheline character varying,
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
    ballot_period integer,
    "timestamp" timestamp without time zone NOT NULL,
    errors character varying,
    utc_year integer NOT NULL,
    utc_month integer NOT NULL,
    utc_day integer NOT NULL,
    utc_time character varying NOT NULL
);

 CREATE INDEX ix_manager_pubkey ON tezos.operations USING btree (manager_pubkey);
 CREATE INDEX ix_operation_group_hash ON tezos.operations USING btree (operation_group_hash);
 CREATE INDEX ix_originated_contracts ON tezos.operations USING btree (originated_contracts);
--
-- Name: operations_operation_id_seq; Type: SEQUENCE; Schema: tezos; Owner: -
--

CREATE SEQUENCE tezos.operations_operation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: operations_operation_id_seq; Type: SEQUENCE OWNED BY; Schema: tezos; Owner: -
--

ALTER SEQUENCE tezos.operations_operation_id_seq OWNED BY tezos.operations.operation_id;

CREATE TABLE tezos.big_maps (
    big_map_id numeric PRIMARY KEY,
    key_type character varying,
    value_type character varying
);

CREATE TABLE tezos.big_map_contents (
    big_map_id numeric NOT NULL,
    key character varying,
    key_hash character varying,
    operation_group_id character varying,
    value character varying,
    PRIMARY KEY (big_map_id, key)
);

CREATE TABLE tezos.originated_account_maps (
    big_map_id numeric,
    account_id character varying,
    PRIMARY KEY (big_map_id, account_id)
);

CREATE INDEX accounts_maps_idx ON tezos.originated_account_maps USING btree (account_id);

--
-- Name: balance_updates id; Type: DEFAULT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.balance_updates ALTER COLUMN id SET DEFAULT nextval('tezos.balance_updates_id_seq'::regclass);


--
-- Name: operations operation_id; Type: DEFAULT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations ALTER COLUMN operation_id SET DEFAULT nextval('tezos.operations_operation_id_seq'::regclass);


--
-- Name: operation_groups OperationGroups_pkey; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operation_groups
    ADD CONSTRAINT "OperationGroups_pkey" PRIMARY KEY (block_id, hash);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);


--
-- Name: baking_rights baking_rights_pkey; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.baking_rights
    ADD CONSTRAINT baking_rights_pkey PRIMARY KEY (level, delegate);


--
-- Name: balance_updates balance_updates_key; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.balance_updates
    ADD CONSTRAINT balance_updates_key PRIMARY KEY (id);


--
-- Name: blocks blocks_hash_key; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.blocks
    ADD CONSTRAINT blocks_hash_key UNIQUE (hash);


--
-- Name: bakers bakers_pkey; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.bakers
    ADD CONSTRAINT bakers_pkey PRIMARY KEY (pkh);


--
-- Name: endorsing_rights endorsing_rights_pkey; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.endorsing_rights
    ADD CONSTRAINT endorsing_rights_pkey PRIMARY KEY (level, delegate, slot);


--
-- Name: operations operationId; Type: CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations
    ADD CONSTRAINT "operationId" PRIMARY KEY (operation_id);


--
-- Name: baking_rights_level_idx; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX baking_rights_level_idx ON tezos.baking_rights USING btree (level);

CREATE INDEX baking_rights_delegate_idx ON tezos.baking_rights USING btree (delegate);

CREATE INDEX ix_delegate_priority ON tezos.baking_rights USING btree (delegate, priority);

--
-- Name: endorsing_rights_level_idx; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX endorsing_rights_level_idx ON tezos.endorsing_rights USING btree (level);

CREATE INDEX endorsing_rights_delegate_idx ON tezos.endorsing_rights USING btree (delegate);

CREATE INDEX ix_delegate_slot ON tezos.endorsing_rights USING btree (delegate, slot);
--
-- Name: fki_block; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX fki_block ON tezos.operation_groups USING btree (block_id);


--
-- Name: fki_fk_block_hash; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX fki_fk_block_hash ON tezos.baking_rights USING btree (block_hash);


--
-- Name: fki_fk_block_hash2; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX fki_fk_block_hash2 ON tezos.endorsing_rights USING btree (block_hash);


--
-- Name: fki_fk_blockhashes; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX fki_fk_blockhashes ON tezos.operations USING btree (block_hash);


--
-- Name: ix_accounts_block_level; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_accounts_block_level ON tezos.accounts USING btree (block_level);

--
-- Name: ix_accounts_is_activated; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_accounts_is_activated ON tezos.accounts USING btree (is_activated);

--
-- Name: ix_accounts_checkpoint_account_id; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_account_id ON tezos.accounts_checkpoint USING btree (account_id);


--
-- Name: ix_accounts_checkpoint_block_level; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_accounts_checkpoint_block_level ON tezos.accounts_checkpoint USING btree (block_level);


--
-- Name: ix_accounts_manager; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_accounts_manager ON tezos.accounts USING btree (manager);

CREATE INDEX ix_accounts_block_id ON tezos.accounts USING btree (block_id);

CREATE INDEX ix_accounts_history_block_id ON tezos.accounts_history USING btree (block_id);

--
-- Name: ix_blocks_level; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_blocks_level ON tezos.blocks USING btree (level);


--
-- Name: ix_delegates_checkpoint_block_level; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_bakers_checkpoint_block_level ON tezos.bakers_checkpoint USING btree (block_level);


--
-- Name: ix_operation_groups_block_level; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_operation_groups_block_level ON tezos.operation_groups USING btree (block_level);


--
-- Name: ix_operations_block_level; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_operations_block_level ON tezos.operations USING btree (block_level);


--
-- Name: ix_operations_delegate; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_operations_delegate ON tezos.operations USING btree (delegate);


--
-- Name: ix_operations_destination; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_operations_destination ON tezos.operations USING btree (destination);


--
-- Name: ix_operations_source; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_operations_source ON tezos.operations USING btree (source);


--
-- Name: ix_operations_timestamp; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX ix_operations_timestamp ON tezos.operations USING btree ("timestamp");

CREATE INDEX ix_operations_cycle ON tezos.operations USING btree("cycle");

CREATE INDEX ix_operations_kind ON tezos.operations USING btree("kind");

CREATE INDEX ix_balance_updates_op_group_hash ON tezos.balance_updates USING btree (operation_group_hash);

CREATE INDEX ix_balance_updates_account_id ON tezos.balance_updates USING btree (account_id);

CREATE INDEX ix_balance_updates_block_level ON tezos.balance_updates USING btree (block_level);

--
-- Name: accounts accounts_block_id_fkey; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.accounts
    ADD CONSTRAINT accounts_block_id_fkey FOREIGN KEY (block_id) REFERENCES tezos.blocks(hash);


--
-- Name: operation_groups block; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operation_groups
    ADD CONSTRAINT block FOREIGN KEY (block_id) REFERENCES tezos.blocks(hash);


--
-- TOC entry 2119 (class 2606 OID 99741)
-- Name: delegates_checkpoint delegate_checkpoint_block_id_fkey; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.bakers_checkpoint
    ADD CONSTRAINT baker_checkpoint_block_id_fkey FOREIGN KEY (block_id) REFERENCES tezos.blocks(hash);


--
-- Name: delegates delegates_block_id_fkey; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.bakers
    ADD CONSTRAINT bakers_block_id_fkey FOREIGN KEY (block_id) REFERENCES tezos.blocks(hash);


--
-- Name: baking_rights fk_block_hash; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.baking_rights
    ADD CONSTRAINT fk_block_hash FOREIGN KEY (block_hash) REFERENCES tezos.blocks(hash) NOT VALID;


--
-- Name: endorsing_rights fk_block_hash; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.endorsing_rights
    ADD CONSTRAINT fk_block_hash FOREIGN KEY (block_hash) REFERENCES tezos.blocks(hash) NOT VALID;


--
-- Name: operations fk_blockhashes; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations
    ADD CONSTRAINT fk_blockhashes FOREIGN KEY (block_hash) REFERENCES tezos.blocks(hash);


--
-- Name: operations fk_opgroups; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations
    ADD CONSTRAINT fk_opgroups FOREIGN KEY (operation_group_hash, block_hash) REFERENCES tezos.operation_groups(hash, block_id);

--
-- PostgreSQL database dump complete
--

CREATE SCHEMA bitcoin;

-- https://developer.bitcoin.org/reference/rpc/getblock.html
CREATE TABLE bitcoin.blocks (
  hash text NOT NULL PRIMARY KEY,
  size integer NOT NULL,
  stripped_size integer NOT NULL,
  weight integer NOT NULL,
  height integer NOT NULL,
  version integer NOT NULL,
  version_hex text NOT NULL,
  merkle_root text NOT NULL,
  nonce bigint NOT NULL,
  bits text NOT NULL,
  difficulty numeric NOT NULL,
  chain_work text NOT NULL,
  n_tx integer NOT NULL,
  previous_block_hash text,
  next_block_hash text,
  median_time timestamp without time zone NOT NULL,
  time timestamp without time zone NOT NULL
);

-- https://developer.bitcoin.org/reference/rpc/getrawtransaction.html
CREATE TABLE bitcoin.transactions (
  txid text NOT NULL PRIMARY KEY,
  blockhash text NOT NULL,
  hash text NOT NULL,
  hex text NOT NULL,
  size integer NOT NULL,
  vsize integer NOT NULL,
  weight integer NOT NULL,
  version integer NOT NULL,
  lock_time timestamp without time zone NOT NULL,
  block_time timestamp without time zone NOT NULL,
  time timestamp without time zone NOT NULL
);

ALTER TABLE ONLY bitcoin.transactions
  ADD CONSTRAINT bitcoin_transactions_blockhash_fkey FOREIGN KEY (blockhash) REFERENCES bitcoin.blocks(hash);

CREATE TABLE bitcoin.inputs (
  txid text NOT NULL,
  v_out integer,
  script_sig_asm text,
  script_sig_hex text,
  sequence bigint NOT NULL,
  coinbase text,
  tx_in_witness text
);

ALTER TABLE ONLY bitcoin.inputs
  ADD CONSTRAINT bitcoin_inputs_txid_fkey FOREIGN KEY (txid) REFERENCES bitcoin.transactions(txid);

CREATE TABLE bitcoin.outputs (
  txid text NOT NULL,
  value numeric,
  n integer NOT NULL,
  script_pub_key_asm text NOT NULL,
  script_pub_key_hex text NOT NULL,
  script_pub_key_req_sigs integer,
  script_pub_key_type text NOT NULL,
  script_pub_key_addresses text
);

ALTER TABLE ONLY bitcoin.outputs
  ADD CONSTRAINT bitcoin_outputs_txid_fkey FOREIGN KEY (txid) REFERENCES bitcoin.transactions(txid);
