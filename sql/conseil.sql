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
    level bigint,
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
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (block_hash, proposal_hash, voting_period_kind, fork_id)
);

CREATE INDEX governance_block_hash_idx ON tezos.governance USING btree (block_hash);
CREATE INDEX governance_proposal_hash_idx ON tezos.governance USING btree (proposal_hash);



CREATE TABLE tezos.processed_chain_events (
    event_level bigint,
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
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    asof timestamp without time zone NOT NULL,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (token_id, address, block_level, fork_id)
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
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    manager character varying, -- retro-compat from protocol 5+
    spendable boolean, -- retro-compat from protocol 5+
    delegate_setable boolean, -- retro-compat from protocol 5+
    delegate_value char varying, -- retro-compat from protocol 5+
    is_baker boolean NOT NULL DEFAULT false,
    is_activated boolean NOT NULL DEFAULT false,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    script_hash character varying,
    PRIMARY KEY (account_id, fork_id)
);


CREATE TABLE tezos.accounts_history (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    counter integer,
    storage character varying,
    balance numeric NOT NULL,
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    delegate_value char varying, -- retro-compat from protocol 5+
    asof timestamp without time zone NOT NULL,
    is_baker boolean NOT NULL DEFAULT false,
    cycle integer,
    is_activated boolean NOT NULL DEFAULT false,
    is_active_baker boolean,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    script_hash character varying
);

CREATE INDEX ix_account_id ON tezos.accounts_history USING btree (account_id);

--
-- Name: accounts_checkpoint; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.accounts_checkpoint (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    asof timestamp with time zone NOT NULL,
    cycle integer
);


--
-- Name: baking_rights; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.baking_rights (
    block_hash character varying,
    block_level bigint NOT NULL,
    delegate character varying NOT NULL,
    priority integer NOT NULL,
    estimated_time timestamp without time zone,
    cycle integer,
    governance_period integer,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (block_level, delegate, fork_id)
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
    level bigint,
    category character varying,
    operation_group_hash character varying,
    block_id character varying NOT NULL,
    block_level bigint NOT NULL,
    cycle integer,
    period integer,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (id, fork_id)
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
    level bigint NOT NULL,
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
    meta_level bigint,
    meta_level_position integer,
    meta_cycle integer,
    meta_cycle_position integer,
    meta_voting_period integer,
    meta_voting_period_position integer,
    priority integer,
    utc_year integer NOT NULL,
    utc_month integer NOT NULL,
    utc_day integer NOT NULL,
    utc_time character varying NOT NULL,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    UNIQUE (hash, fork_id)
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
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    cycle integer,
    period integer,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (pkh, fork_id)
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
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    cycle integer,
    period integer,
    asof timestamp without time zone NOT NULL,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL
);

--
-- Name: bakers_checkpoint; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.bakers_checkpoint (
    delegate_pkh character varying NOT NULL,
    block_id character varying NOT NULL,
    block_level bigint DEFAULT '-1'::integer NOT NULL,
    cycle integer,
    period integer
);


--
-- Name: endorsing_rights; Type: TABLE; Schema: tezos; Owner: -
--

CREATE TABLE tezos.endorsing_rights (
    block_hash character varying,
    block_level bigint NOT NULL,
    delegate character varying NOT NULL,
    slot integer NOT NULL,
    estimated_time timestamp without time zone,
    cycle integer,
    governance_period integer,
    endorsed_block bigint,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (block_level, delegate, slot, fork_id)
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
    level bigint,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL
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
    block_level bigint NOT NULL,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (block_id, hash, fork_id)
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
    level bigint ,
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
    block_level bigint NOT NULL,
    ballot character varying,
    internal boolean NOT NULL,
    period integer,
    ballot_period integer,
    "timestamp" timestamp without time zone NOT NULL,
    errors character varying,
    utc_year integer NOT NULL,
    utc_month integer NOT NULL,
    utc_day integer NOT NULL,
    utc_time character varying NOT NULL,
    invalidated_asof timestamp,
    fork_id character varying NOT NULL,
    PRIMARY KEY (operation_id, fork_id)
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
    block_level bigint,
    "timestamp" timestamp without time zone,
    cycle integer,
    period integer,
    PRIMARY KEY (big_map_id, key)
);

CREATE INDEX big_map_id_idx ON tezos.big_map_contents USING btree (big_map_id);
CREATE INDEX operation_group_id_idx ON tezos.big_map_contents USING btree (operation_group_id);
CREATE INDEX combined_big_map_operation_group_ids_idx ON tezos.big_map_contents USING btree (big_map_id, operation_group_id);

CREATE TABLE tezos.originated_account_maps (
    big_map_id numeric,
    account_id character varying,
    PRIMARY KEY (big_map_id, account_id)
);

CREATE INDEX accounts_maps_idx ON tezos.originated_account_maps USING btree (account_id);

CREATE TABLE tezos.forks (
    fork_id character varying PRIMARY KEY,
    fork_level bigint NOT NULL,
    fork_hash character varying NOT NULL,
    head_level bigint NOT NULL,
    "timestamp" timestamp without time zone NOT NULL
);
--
-- Name: balance_updates id; Type: DEFAULT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.balance_updates ALTER COLUMN id SET DEFAULT nextval('tezos.balance_updates_id_seq'::regclass);


--
-- Name: operations operation_id; Type: DEFAULT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations ALTER COLUMN operation_id SET DEFAULT nextval('tezos.operations_operation_id_seq'::regclass);

--
-- Name: baking_rights_level_idx; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX baking_rights_level_idx ON tezos.baking_rights USING btree (block_level);

CREATE INDEX baking_rights_delegate_idx ON tezos.baking_rights USING btree (delegate);

CREATE INDEX ix_delegate_priority ON tezos.baking_rights USING btree (delegate, priority);

CREATE INDEX ix_cycle ON tezos.baking_rights USING btree (cycle ASC NULLS LAST);

CREATE INDEX ix_delegate_priority_cycle ON tezos.baking_rights USING btree
    (delegate ASC NULLS LAST, priority ASC NULLS LAST, cycle ASC NULLS LAST);

--
-- Name: endorsing_rights_level_idx; Type: INDEX; Schema: tezos; Owner: -
--

CREATE INDEX endorsing_rights_level_idx ON tezos.endorsing_rights USING btree (block_level);

CREATE INDEX endorsing_rights_delegate_idx ON tezos.endorsing_rights USING btree (delegate);

CREATE INDEX ix_delegate_slot ON tezos.endorsing_rights USING btree (delegate, slot);

CREATE INDEX ix_delegate_block_level ON tezos.endorsing_rights USING btree
    (delegate ASC NULLS LAST, block_level ASC NULLS LAST);

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
    ADD CONSTRAINT accounts_block_id_fkey
    FOREIGN KEY (block_id, fork_id)
    REFERENCES tezos.blocks(hash, fork_id)
    DEFERRABLE INITIALLY IMMEDIATE;


--
-- Name: operation_groups block; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operation_groups
    ADD CONSTRAINT block
    FOREIGN KEY (block_id, fork_id)
    REFERENCES tezos.blocks(hash, fork_id)
    DEFERRABLE INITIALLY IMMEDIATE;

--
-- Name: delegates delegates_block_id_fkey; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.bakers
    ADD CONSTRAINT bakers_block_id_fkey
    FOREIGN KEY (block_id, fork_id)
    REFERENCES tezos.blocks(hash, fork_id)
    DEFERRABLE INITIALLY IMMEDIATE;


--
-- Name: baking_rights fk_block_hash; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.baking_rights
    ADD CONSTRAINT bake_rights_block_fkey
    FOREIGN KEY (block_hash, fork_id)
    REFERENCES tezos.blocks(hash, fork_id)
    NOT VALID
    DEFERRABLE INITIALLY IMMEDIATE;


--
-- Name: endorsing_rights fk_block_hash; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.endorsing_rights
    ADD CONSTRAINT endorse_rights_block_fkey
    FOREIGN KEY (block_hash, fork_id)
    REFERENCES tezos.blocks(hash, fork_id)
    NOT VALID
    DEFERRABLE INITIALLY IMMEDIATE;


--
-- Name: operations fk_blockhashes; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations
    ADD CONSTRAINT fk_blockhashes
    FOREIGN KEY (block_hash, fork_id)
    REFERENCES tezos.blocks(hash, fork_id)
    DEFERRABLE INITIALLY IMMEDIATE;


--
-- Name: operations fk_opgroups; Type: FK CONSTRAINT; Schema: tezos; Owner: -
--

ALTER TABLE ONLY tezos.operations
    ADD CONSTRAINT fk_opgroups
    FOREIGN KEY (operation_group_hash, block_hash, fork_id)
    REFERENCES tezos.operation_groups(hash, block_id, fork_id)
    DEFERRABLE INITIALLY IMMEDIATE;

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
  output_txid text, -- output id this input spends
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

CREATE OR REPLACE VIEW bitcoin.accounts AS
SELECT
  script_pub_key_addresses AS address,
  SUM(
    CASE WHEN bitcoin.inputs.output_txid IS NULL THEN
      value
    ELSE
      0
    END) AS value
FROM
  bitcoin.outputs
  LEFT JOIN bitcoin.inputs ON bitcoin.outputs.txid = bitcoin.inputs.output_txid
    AND bitcoin.outputs.n = bitcoin.inputs.v_out
  GROUP BY
    bitcoin.outputs.script_pub_key_addresses;

CREATE SCHEMA ethereum;

-- Table is based on eth_getBlockByHash from https://eth.wiki/json-rpc/API
CREATE TABLE ethereum.blocks (
  hash text NOT NULL PRIMARY KEY,
  level integer NOT NULL, -- number
  difficulty numeric NOT NULL,
  extra_data text NOT NULL,
  gas_limit numeric NOT NULL,
  gas_used numeric NOT NULL,
  logs_bloom text NOT NULL,
  miner text NOT NULL,
  mix_hash text NOT NULL,
  nonce text NOT NULL,
  parent_hash text,
  receipts_root text NOT NULL,
  sha3_uncles text NOT NULL,
  size integer NOT NULL,
  state_root text NOT NULL,
  total_difficulty numeric NOT NULL,
  transactions_root text NOT NULL,
  uncles text,
  timestamp timestamp without time zone NOT NULL
);

-- Table is based on eth_getTransactionByHash from https://eth.wiki/json-rpc/API
CREATE TABLE ethereum.transactions (
  hash text NOT NULL PRIMARY KEY,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  timestamp timestamp without time zone,
  source text NOT NULL, -- from
  gas numeric NOT NULL,
  gas_price numeric NOT NULL,
  input text NOT NULL,
  nonce text NOT NULL,
  destination text, -- to
  transaction_index integer NOT NULL,
  amount numeric NOT NULL, -- value in wei
  v text NOT NULL,
  r text NOT NULL,
  s text NOT NULL
);

ALTER TABLE ONLY ethereum.transactions
  ADD CONSTRAINT ethereum_transactions_block_hash_fkey FOREIGN KEY (block_hash) REFERENCES ethereum.blocks(hash);

-- Table is based on eth_getTransactionReceipt from https://eth.wiki/json-rpc/API
CREATE TABLE ethereum.receipts (
  transaction_hash text NOT NULL,
  transaction_index integer NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  timestamp timestamp without time zone,
  contract_address text,
  cumulative_gas_used numeric NOT NULL,
  gas_used numeric NOT NULL,
  logs_bloom text NOT NULL,
  status text,
  root text
);

-- Table is based on eth_getLogs from https://eth.wiki/json-rpc/API
CREATE TABLE ethereum.logs (
  address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  timestamp timestamp without time zone,
  data text NOT NULL,
  log_index integer NOT NULL,
  removed boolean NOT NULL,
  topics text NOT NULL,
  transaction_hash text NOT NULL,
  transaction_index integer NOT NULL
);

ALTER TABLE ONLY ethereum.logs
  ADD CONSTRAINT ethereum_logs_block_hash_fkey FOREIGN KEY (block_hash) REFERENCES ethereum.blocks(hash);

CREATE TABLE ethereum.token_transfers (
  token_address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  timestamp timestamp without time zone,
  transaction_hash text NOT NULL,
  log_index text NOT NULL,
  from_address text NOT NULL,
  to_address text NOT NULL,
  value numeric NOT NULL
);

CREATE TABLE ethereum.tokens_history (
  account_address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  transaction_hash text NOT NULL,
  token_address text NOT NULL,
  value numeric NOT NULL,
  asof timestamp without time zone NOT NULL
);

CREATE INDEX ix_account_address ON ethereum.tokens_history USING btree (account_address);
CREATE INDEX ix_token_address ON ethereum.tokens_history USING btree (token_address);

CREATE TABLE ethereum.accounts (
  address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  timestamp timestamp without time zone,
  balance numeric NOT NULL,
  bytecode text,
  token_standard text,
  name text,
  symbol text,
  decimals text,
  total_supply text,
  PRIMARY KEY (address)
);

CREATE INDEX ix_accounts_address ON ethereum.accounts USING btree (address);

CREATE TABLE ethereum.accounts_history (
  address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  balance numeric NOT NULL,
  asof timestamp without time zone NOT NULL,
  PRIMARY KEY (address, block_number)
);

CREATE INDEX ix_accounts_history_address ON ethereum.accounts_history USING btree (address);

CREATE OR REPLACE VIEW ethereum.tokens AS
SELECT
  address,
  block_hash,
  block_number,
  timestamp,
  name,
  symbol,
  decimals,
  total_supply
FROM
  ethereum.accounts
WHERE
  token_standard IS NOT NULL
;

CREATE OR REPLACE VIEW ethereum.contracts AS
SELECT
  address,
  block_hash,
  block_number,
  timestamp,
  bytecode,
  token_standard
FROM
  ethereum.accounts
WHERE
  bytecode IS NOT NULL
;


-- The schema for Quorum is duplicated from Ethereum.
-- TODO: This is a temporary solution, in the future we intend to generate the schema automatically to avoid duplication,
--       but it requires changes to the whole Conseil project.
CREATE SCHEMA quorum;

-- Table is based on eth_getBlockByHash from https://eth.wiki/json-rpc/API
CREATE TABLE quorum.blocks (
  hash text NOT NULL PRIMARY KEY,
  number integer NOT NULL,
  difficulty text NOT NULL,
  extra_data text NOT NULL,
  gas_limit text NOT NULL,
  gas_used text NOT NULL,
  logs_bloom text NOT NULL,
  miner text NOT NULL,
  mix_hash text NOT NULL,
  nonce text NOT NULL,
  parent_hash text,
  receipts_root text NOT NULL,
  sha3_uncles text NOT NULL,
  size text NOT NULL,
  state_root text NOT NULL,
  total_difficulty text NOT NULL,
  transactions_root text NOT NULL,
  uncles text,
  timestamp timestamp without time zone NOT NULL
);

-- Table is based on eth_getTransactionByHash from https://eth.wiki/json-rpc/API
CREATE TABLE quorum.transactions (
  hash text NOT NULL PRIMARY KEY,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  "from" text NOT NULL,
  gas text NOT NULL,
  gas_price text NOT NULL,
  input text NOT NULL,
  nonce text NOT NULL,
  "to" text,
  transaction_index text NOT NULL,
  value numeric NOT NULL, -- value in wei
  v text NOT NULL,
  r text NOT NULL,
  s text NOT NULL
);

ALTER TABLE ONLY quorum.transactions
  ADD CONSTRAINT quorum_transactions_block_hash_fkey FOREIGN KEY (block_hash) REFERENCES quorum.blocks(hash);

-- Table is based on eth_getTransactionReceipt from https://eth.wiki/json-rpc/API
CREATE TABLE quorum.receipts (
  transaction_hash text NOT NULL,
  transaction_index text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  contract_address text,
  cumulative_gas_used text NOT NULL,
  gas_used text NOT NULL,
  logs_bloom text NOT NULL,
  status text,
  root text
);

-- Table is based on eth_getLogs from https://eth.wiki/json-rpc/API
CREATE TABLE quorum.logs (
  address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  data text NOT NULL,
  log_index text NOT NULL,
  removed boolean NOT NULL,
  topics text NOT NULL,
  transaction_hash text NOT NULL,
  transaction_index text NOT NULL
);

ALTER TABLE ONLY quorum.logs
  ADD CONSTRAINT quorum_logs_block_hash_fkey FOREIGN KEY (block_hash) REFERENCES quorum.blocks(hash);

CREATE TABLE quorum.contracts (
  address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  bytecode text NOT NULL,
  is_erc20 boolean NOT NULL DEFAULT false,
  is_erc721 boolean NOT NULL DEFAULT false
);

CREATE TABLE quorum.tokens (
  address text NOT NULL,
  block_hash text NOT NULL,
  block_number integer NOT NULL,
  name text NOT NULL,
  symbol text NOT NULL,
  decimals text NOT NULL,
  total_supply text NOT NULL
);

CREATE TABLE quorum.token_transfers (
  block_number integer NOT NULL,
  transaction_hash text NOT NULL,
  from_address text NOT NULL,
  to_address text NOT NULL,
  value numeric NOT NULL
);

CREATE OR REPLACE VIEW quorum.accounts AS
SELECT
  "to" AS address,
  SUM(value) AS value
FROM
  quorum.transactions
GROUP BY
  "to";
