--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.10
-- Dumped by pg_dump version 9.5.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner:
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner:
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE accounts (
    account_id character varying NOT NULL,
    block_id character varying NOT NULL,
    manager character varying NOT NULL,
    spendable boolean NOT NULL,
    delegate_setable boolean NOT NULL,
    delegate_value character varying,
    counter integer NOT NULL
);


ALTER TABLE accounts OWNER TO vishakh;

--
-- Name: ballots; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE ballots (
    ballot_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    period integer NOT NULL,
    proposal character varying NOT NULL,
    ballot character varying NOT NULL
);


ALTER TABLE ballots OWNER TO vishakh;

--
-- Name: ballots_ballot_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE ballots_ballot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE ballots_ballot_id_seq OWNER TO vishakh;

--
-- Name: ballots_ballot_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE ballots_ballot_id_seq OWNED BY ballots.ballot_id;


--
-- Name: blocks; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE blocks (
    net_id character varying NOT NULL,
    protocol character varying NOT NULL,
    level integer NOT NULL,
    proto integer NOT NULL,
    predecessor character varying NOT NULL,
    validation_pass integer NOT NULL,
    operations_hash character varying NOT NULL,
    data character varying NOT NULL,
    hash character varying NOT NULL,
    "timestamp" timestamp without time zone NOT NULL,
    fitness character varying NOT NULL
);


ALTER TABLE blocks OWNER TO vishakh;

--
-- Name: delegations; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE delegations (
    delegation_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    delegate character varying NOT NULL
);


ALTER TABLE delegations OWNER TO vishakh;

--
-- Name: delegations_delegation_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE delegations_delegation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE delegations_delegation_id_seq OWNER TO vishakh;

--
-- Name: delegations_delegation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE delegations_delegation_id_seq OWNED BY delegations.delegation_id;


--
-- Name: endorsements; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE endorsements (
    endorsement_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    block_id character varying NOT NULL,
    slot integer NOT NULL
);


ALTER TABLE endorsements OWNER TO vishakh;

--
-- Name: endorsements_endorsement_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE endorsements_endorsement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE endorsements_endorsement_id_seq OWNER TO vishakh;

--
-- Name: endorsements_endorsement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE endorsements_endorsement_id_seq OWNED BY endorsements.endorsement_id;


--
-- Name: faucet_transactions; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE faucet_transactions (
    faucet_transaction_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    id character varying NOT NULL,
    nonce character varying NOT NULL
);


ALTER TABLE faucet_transactions OWNER TO vishakh;

--
-- Name: faucet_transactions_faucet_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE faucet_transactions_faucet_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE faucet_transactions_faucet_transaction_id_seq OWNER TO vishakh;

--
-- Name: faucet_transactions_faucet_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE faucet_transactions_faucet_transaction_id_seq OWNED BY faucet_transactions.faucet_transaction_id;


--
-- Name: operation_groups; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE operation_groups (
    hash character varying NOT NULL,
    block_id character varying NOT NULL,
    branch character varying NOT NULL,
    source character varying,
    signature character varying
);


ALTER TABLE operation_groups OWNER TO vishakh;

--
-- Name: originations; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE originations (
    origination_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    "managerPubkey" character varying,
    balance numeric,
    spendable boolean,
    delegatable boolean,
    delegate character varying,
    script character varying
);


ALTER TABLE originations OWNER TO vishakh;

--
-- Name: originations_origination_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE originations_origination_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE originations_origination_id_seq OWNER TO vishakh;

--
-- Name: originations_origination_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE originations_origination_id_seq OWNED BY originations.origination_id;


--
-- Name: proposals; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE proposals (
    proposal_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    period integer NOT NULL,
    proposal character varying NOT NULL
);


ALTER TABLE proposals OWNER TO vishakh;

--
-- Name: proposals_proposal_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE proposals_proposal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE proposals_proposal_id_seq OWNER TO vishakh;

--
-- Name: proposals_proposal_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE proposals_proposal_id_seq OWNED BY proposals.proposal_id;


--
-- Name: seed_nonce_revealations; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE seed_nonce_revealations (
    seed_nonnce_revealation_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    level integer NOT NULL,
    nonce character varying NOT NULL
);


ALTER TABLE seed_nonce_revealations OWNER TO vishakh;

--
-- Name: seed_nonce_revealations_seed_nonnce_revealation_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE seed_nonce_revealations_seed_nonnce_revealation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE seed_nonce_revealations_seed_nonnce_revealation_id_seq OWNER TO vishakh;

--
-- Name: seed_nonce_revealations_seed_nonnce_revealation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE seed_nonce_revealations_seed_nonnce_revealation_id_seq OWNED BY seed_nonce_revealations.seed_nonnce_revealation_id;


--
-- Name: transactions; Type: TABLE; Schema: public; Owner: vishakh
--

CREATE TABLE transactions (
    transaction_id integer NOT NULL,
    operation_group_hash character varying NOT NULL,
    amount numeric NOT NULL,
    destination character varying,
    parameters character varying
);


ALTER TABLE transactions OWNER TO vishakh;

--
-- Name: transactions_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: vishakh
--

CREATE SEQUENCE transactions_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE transactions_transaction_id_seq OWNER TO vishakh;

--
-- Name: transactions_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: vishakh
--

ALTER SEQUENCE transactions_transaction_id_seq OWNED BY transactions.transaction_id;


--
-- Name: ballot_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY ballots ALTER COLUMN ballot_id SET DEFAULT nextval('ballots_ballot_id_seq'::regclass);


--
-- Name: delegation_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY delegations ALTER COLUMN delegation_id SET DEFAULT nextval('delegations_delegation_id_seq'::regclass);


--
-- Name: endorsement_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY endorsements ALTER COLUMN endorsement_id SET DEFAULT nextval('endorsements_endorsement_id_seq'::regclass);


--
-- Name: faucet_transaction_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY faucet_transactions ALTER COLUMN faucet_transaction_id SET DEFAULT nextval('faucet_transactions_faucet_transaction_id_seq'::regclass);


--
-- Name: origination_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY originations ALTER COLUMN origination_id SET DEFAULT nextval('originations_origination_id_seq'::regclass);


--
-- Name: proposal_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY proposals ALTER COLUMN proposal_id SET DEFAULT nextval('proposals_proposal_id_seq'::regclass);


--
-- Name: seed_nonnce_revealation_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY seed_nonce_revealations ALTER COLUMN seed_nonnce_revealation_id SET DEFAULT nextval('seed_nonce_revealations_seed_nonnce_revealation_id_seq'::regclass);


--
-- Name: transaction_id; Type: DEFAULT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY transactions ALTER COLUMN transaction_id SET DEFAULT nextval('transactions_transaction_id_seq'::regclass);


--
-- Name: OperationGroups_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY operation_groups
    ADD CONSTRAINT "OperationGroups_pkey" PRIMARY KEY (hash);


--
-- Name: accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id, block_id);


--
-- Name: ballots_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY ballots
    ADD CONSTRAINT ballots_pkey PRIMARY KEY (ballot_id);


--
-- Name: blocks_hash_key; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY blocks
    ADD CONSTRAINT blocks_hash_key UNIQUE (hash);


--
-- Name: delegations_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY delegations
    ADD CONSTRAINT delegations_pkey PRIMARY KEY (delegation_id);


--
-- Name: endorsements_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY endorsements
    ADD CONSTRAINT endorsements_pkey PRIMARY KEY (endorsement_id);


--
-- Name: faucet_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY faucet_transactions
    ADD CONSTRAINT faucet_transactions_pkey PRIMARY KEY (faucet_transaction_id);


--
-- Name: originations_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY originations
    ADD CONSTRAINT originations_pkey PRIMARY KEY (origination_id);


--
-- Name: proposals_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY proposals
    ADD CONSTRAINT proposals_pkey PRIMARY KEY (proposal_id);


--
-- Name: seed_nonce_revealations_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY seed_nonce_revealations
    ADD CONSTRAINT seed_nonce_revealations_pkey PRIMARY KEY (seed_nonnce_revealation_id);


--
-- Name: transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (transaction_id);


--
-- Name: blocks_hash_idx; Type: INDEX; Schema: public; Owner: vishakh
--

CREATE INDEX blocks_hash_idx ON blocks USING btree (hash);


--
-- Name: operation_groups_hash_idx; Type: INDEX; Schema: public; Owner: vishakh
--

CREATE INDEX operation_groups_hash_idx ON operation_groups USING btree (hash);


--
-- Name: OperationGroups_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY operation_groups
    ADD CONSTRAINT "OperationGroups_block_id_fkey" FOREIGN KEY (block_id) REFERENCES blocks(hash);


--
-- Name: accounts_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY accounts
    ADD CONSTRAINT accounts_block_id_fkey FOREIGN KEY (block_id) REFERENCES blocks(hash);


--
-- Name: ballots_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY ballots
    ADD CONSTRAINT ballots_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: blocks_predecessor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY blocks
    ADD CONSTRAINT blocks_predecessor_fkey FOREIGN KEY (predecessor) REFERENCES blocks(hash);


--
-- Name: delegations_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY delegations
    ADD CONSTRAINT delegations_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: endorsements_block_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY endorsements
    ADD CONSTRAINT endorsements_block_id_fkey FOREIGN KEY (block_id) REFERENCES blocks(hash);


--
-- Name: endorsements_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY endorsements
    ADD CONSTRAINT endorsements_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: faucet_transactions_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY faucet_transactions
    ADD CONSTRAINT faucet_transactions_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: originations_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY originations
    ADD CONSTRAINT originations_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: proposals_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY proposals
    ADD CONSTRAINT proposals_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: seed_nonce_revealations_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY seed_nonce_revealations
    ADD CONSTRAINT seed_nonce_revealations_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: transactions_operation_group_hash_fkey; Type: FK CONSTRAINT; Schema: public; Owner: vishakh
--

ALTER TABLE ONLY transactions
    ADD CONSTRAINT transactions_operation_group_hash_fkey FOREIGN KEY (operation_group_hash) REFERENCES operation_groups(hash);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

