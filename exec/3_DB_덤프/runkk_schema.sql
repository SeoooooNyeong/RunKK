--
-- PostgreSQL database dump
--

\restrict OrIV6Vu7X0cEn498fWWxxAyuD2b1ndPPV2TDaLtv1syP2xnfIplCah7zftdyBax

-- Dumped from database version 17.12
-- Dumped by pg_dump version 17.12

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: analytics; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA analytics;


--
-- Name: audit_record_reject_update(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.audit_record_reject_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'audit_record is append-only';
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: alembic_version; Type: TABLE; Schema: analytics; Owner: -
--

CREATE TABLE analytics.alembic_version (
    version_num character varying(32) NOT NULL
);


--
-- Name: analysis_event; Type: TABLE; Schema: analytics; Owner: -
--

CREATE TABLE analytics.analysis_event (
    id bigint NOT NULL,
    session_id text NOT NULL,
    status text NOT NULL,
    error_code text,
    at timestamp with time zone NOT NULL
);


--
-- Name: analysis_event_id_seq; Type: SEQUENCE; Schema: analytics; Owner: -
--

CREATE SEQUENCE analytics.analysis_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: analysis_event_id_seq; Type: SEQUENCE OWNED BY; Schema: analytics; Owner: -
--

ALTER SEQUENCE analytics.analysis_event_id_seq OWNED BY analytics.analysis_event.id;


--
-- Name: coach_message; Type: TABLE; Schema: analytics; Owner: -
--

CREATE TABLE analytics.coach_message (
    id bigint NOT NULL,
    conversation_id text NOT NULL,
    user_id text NOT NULL,
    role text NOT NULL,
    content text NOT NULL,
    model text,
    input_tokens integer,
    output_tokens integer,
    tools text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_coach_message_role CHECK ((role = ANY (ARRAY['user'::text, 'assistant'::text])))
);


--
-- Name: coach_message_id_seq; Type: SEQUENCE; Schema: analytics; Owner: -
--

CREATE SEQUENCE analytics.coach_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: coach_message_id_seq; Type: SEQUENCE OWNED BY; Schema: analytics; Owner: -
--

ALTER SEQUENCE analytics.coach_message_id_seq OWNED BY analytics.coach_message.id;


--
-- Name: llm_model; Type: TABLE; Schema: analytics; Owner: -
--

CREATE TABLE analytics.llm_model (
    model_id text NOT NULL,
    base_url text NOT NULL,
    note text,
    registered_at timestamp with time zone DEFAULT now() NOT NULL,
    activated_at timestamp with time zone,
    active boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_llm_model_active_has_time CHECK (((active = false) OR (activated_at IS NOT NULL)))
);


--
-- Name: session_result; Type: TABLE; Schema: analytics; Owner: -
--

CREATE TABLE analytics.session_result (
    session_id text NOT NULL,
    user_id text NOT NULL,
    started_at timestamp with time zone NOT NULL,
    status text NOT NULL,
    error_code text,
    engine_version text,
    result jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_session_result_done_has_result CHECK (((status = 'done'::text) = (result IS NOT NULL))),
    CONSTRAINT ck_session_result_status CHECK ((status = ANY (ARRAY['pending'::text, 'done'::text, 'failed'::text])))
);


--
-- Name: v_session_result; Type: VIEW; Schema: analytics; Owner: -
--

CREATE VIEW analytics.v_session_result AS
 SELECT session_id,
    user_id,
    started_at,
    updated_at AS analyzed_at,
    engine_version,
    (((result -> 'summary'::text) ->> 'distanceMeters'::text))::integer AS distance_meters,
    (((result -> 'summary'::text) ->> 'activeMs'::text))::bigint AS active_ms,
    (((result -> 'summary'::text) ->> 'paceSecondsPerKm'::text))::integer AS pace_seconds_per_km,
    (((result -> 'summary'::text) ->> 'avgHeartRate'::text))::integer AS avg_heart_rate,
    (((result -> 'summary'::text) ->> 'maxHeartRate'::text))::integer AS max_heart_rate,
    (((result -> 'summary'::text) ->> 'cadenceMedian'::text))::integer AS cadence_median,
    (((result -> 'summary'::text) ->> 'recoveryHeartRate1m'::text))::integer AS recovery_heart_rate_1m,
    ((((result -> 'axes'::text) -> 'intensity'::text) ->> 'score'::text))::integer AS intensity_score,
    ((((result -> 'axes'::text) -> 'endurance'::text) ->> 'score'::text))::integer AS endurance_score,
    ((((result -> 'axes'::text) -> 'stability'::text) ->> 'score'::text))::integer AS stability_score,
    ((((result -> 'axes'::text) -> 'form'::text) ->> 'score'::text))::integer AS form_score,
    ((((result -> 'axes'::text) -> 'fitness'::text) ->> 'score'::text))::integer AS fitness_score,
    ((result -> 'coaching'::text) -> 'flags'::text) AS coaching_flags,
    ((result -> 'feedback'::text) ->> 'headline'::text) AS headline,
    ((result -> 'feedback'::text) ->> 'ruleId'::text) AS rule_id,
    result
   FROM analytics.session_result r
  WHERE (status = 'done'::text);


--
-- Name: ai_purge_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_purge_request (
    member_id bigint NOT NULL,
    status character varying(10) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    requested_at timestamp without time zone NOT NULL,
    next_at timestamp without time zone NOT NULL,
    retention_check_done boolean DEFAULT false NOT NULL,
    lease_token character varying(36),
    lease_until timestamp without time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ai_purge_request_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DONE'::character varying, 'ALERTED'::character varying])::text[])))
);


--
-- Name: audit_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_record (
    id bigint NOT NULL,
    recorded_at timestamp(6) without time zone NOT NULL,
    actor_id character varying(100) NOT NULL,
    actor_role character varying(20) NOT NULL,
    action character varying(30) NOT NULL,
    target_id character varying(100) NOT NULL,
    before_value text,
    after_value text,
    CONSTRAINT ck_audit_record_action CHECK (((action)::text = ANY ((ARRAY['SIGN_UP_CONSENT'::character varying, 'CONSENT_CHANGE'::character varying, 'ROUTE_DISPOSAL'::character varying, 'WITHDRAWAL'::character varying, 'LEADER_TRANSFER'::character varying, 'CREW_DELETE'::character varying, 'KICK'::character varying, 'POST_FORCE_DELETE'::character varying, 'CELEBRITY_REGISTER'::character varying, 'CELEBRITY_DELETE'::character varying, 'SETTING_CHANGE'::character varying, 'OPERATOR_LOGIN_SUCCESS'::character varying, 'OPERATOR_LOGIN_FAILURE'::character varying, 'RANKING_RECOMPUTED'::character varying, 'MANAGER_APPOINT'::character varying, 'MANAGER_DISMISS'::character varying, 'CHAT_FORCE_DELETE'::character varying, 'REPORT_HIDE'::character varying, 'REPORT_DISMISS'::character varying, 'AI_DATA_PURGE_OVERDUE'::character varying, 'WRITER_MODEL_REGISTER'::character varying, 'WRITER_MODEL_ACTIVATE'::character varying, 'WRITER_MODEL_ROLLBACK'::character varying])::text[]))),
    CONSTRAINT ck_audit_record_actor_role CHECK (((actor_role)::text = ANY ((ARRAY['RUNNER'::character varying, 'CREW_LEADER'::character varying, 'CREW_MANAGER'::character varying, 'OPERATOR'::character varying, 'SYSTEM'::character varying])::text[])))
);


--
-- Name: audit_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.audit_record ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.audit_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: avatar; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avatar (
    member_id bigint NOT NULL,
    default_character character varying(20),
    creation_count integer NOT NULL,
    last_created_on date,
    created_on_last_day integer NOT NULL,
    body_shape character varying(10) NOT NULL,
    skin_tone character varying(10) NOT NULL,
    version bigint NOT NULL,
    selected_background character varying(40),
    head_job_id character varying(64),
    pending_face_request character varying(64),
    CONSTRAINT ck_avatar_body_shape CHECK (((body_shape)::text = ANY ((ARRAY['STAGE1'::character varying, 'STAGE2'::character varying, 'STAGE3'::character varying, 'STAGE4'::character varying])::text[]))),
    CONSTRAINT ck_avatar_counts CHECK (((creation_count >= 0) AND (created_on_last_day >= 0))),
    CONSTRAINT ck_avatar_default_character CHECK (((default_character IS NULL) OR ((default_character)::text = ANY ((ARRAY['RABBIT'::character varying, 'BEAR'::character varying, 'FOX'::character varying, 'CAT'::character varying])::text[])))),
    CONSTRAINT ck_avatar_skin_tone CHECK (((skin_tone)::text = ANY ((ARRAY['DAWN'::character varying, 'MORNING'::character varying, 'DAYTIME'::character varying, 'EVENING'::character varying, 'NIGHT'::character varying])::text[])))
);


--
-- Name: avatar_background; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avatar_background (
    member_id bigint NOT NULL,
    landmark_key character varying(40) NOT NULL,
    landmark_name character varying(40) NOT NULL,
    stage character varying(20) NOT NULL,
    distance_meters bigint NOT NULL,
    unlocked_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_avatar_background_stage CHECK (((stage)::text = ANY ((ARRAY['DOMESTIC'::character varying, 'EAST_ASIA'::character varying, 'INTERCONTINENTAL'::character varying])::text[])))
);


--
-- Name: avatar_equipment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avatar_equipment (
    member_id bigint NOT NULL,
    slot character varying(10) NOT NULL,
    item_id bigint NOT NULL,
    CONSTRAINT ck_avatar_equipment_slot CHECK (((slot)::text = ANY ((ARRAY['TOP'::character varying, 'BOTTOM'::character varying, 'SHOES'::character varying, 'HAT'::character varying])::text[])))
);


--
-- Name: avatar_face_part; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avatar_face_part (
    member_id bigint NOT NULL,
    part character varying(10) NOT NULL,
    image_key character varying(200) NOT NULL,
    CONSTRAINT ck_avatar_face_part CHECK (((part)::text = ANY ((ARRAY['EYES'::character varying, 'NOSE'::character varying, 'MOUTH'::character varying, 'EARS'::character varying, 'HAIR'::character varying])::text[])))
);


--
-- Name: battle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.battle (
    id bigint NOT NULL,
    type character varying(10) NOT NULL,
    distance_meters bigint NOT NULL,
    status character varying(15) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    start_at timestamp(6) without time zone,
    countdown_ends_at timestamp(6) without time zone,
    max_ends_at timestamp(6) without time zone,
    closed_at timestamp(6) without time zone,
    opponent_kind character varying(12),
    opponent_record_id character varying(36),
    opponent_name character varying(40),
    opponent_tier character varying(10),
    opponent_arrival_millis bigint,
    opponent_track text,
    opponent_pace_seconds integer,
    opponent_fastest_km_seconds integer,
    opponent_arrives_at timestamp(6) without time zone,
    opponent_arrived_at timestamp(6) without time zone,
    opponent_verdict character varying(10),
    judged_at timestamp(6) without time zone,
    corrected boolean NOT NULL,
    winner_member_id bigint,
    winner_opponent boolean NOT NULL,
    version bigint NOT NULL,
    opponent_gender character varying(6),
    opponent_figure character varying(20),
    placement_round smallint,
    CONSTRAINT ck_battle_distance CHECK ((distance_meters > 0)),
    CONSTRAINT ck_battle_opponent CHECK (((((type)::text = ANY ((ARRAY['RANDOM'::character varying, 'FRIEND'::character varying])::text[])) AND (opponent_kind IS NULL)) OR (((type)::text <> ALL ((ARRAY['RANDOM'::character varying, 'FRIEND'::character varying])::text[])) AND (opponent_kind IS NOT NULL)))),
    CONSTRAINT ck_battle_opponent_gender CHECK (((opponent_gender IS NULL) OR ((opponent_gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying])::text[])))),
    CONSTRAINT ck_battle_opponent_kind CHECK (((opponent_kind IS NULL) OR ((opponent_kind)::text = ANY ((ARRAY['RUN_RECORD'::character varying, 'VIRTUAL'::character varying, 'CELEBRITY'::character varying])::text[])))),
    CONSTRAINT ck_battle_placement_round CHECK (((placement_round IS NULL) OR (((placement_round >= 1) AND (placement_round <= 3)) AND ((type)::text = 'GHOST'::text)))),
    CONSTRAINT ck_battle_status CHECK (((status)::text = ANY ((ARRAY['WAITING_START'::character varying, 'IN_PROGRESS'::character varying, 'COUNTDOWN'::character varying, 'RESULT_PENDING'::character varying, 'FINISHED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_battle_type CHECK (((type)::text = ANY ((ARRAY['RANDOM'::character varying, 'GHOST'::character varying, 'FRIEND'::character varying, 'SELF'::character varying, 'CELEBRITY'::character varying])::text[])))
);


--
-- Name: battle_disconnection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.battle_disconnection (
    participant_id bigint NOT NULL,
    seq integer NOT NULL,
    disconnected_at timestamp(6) without time zone NOT NULL,
    reconnected_at timestamp(6) without time zone
);


--
-- Name: battle_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.battle ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.battle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: battle_participant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.battle_participant (
    id bigint NOT NULL,
    battle_id bigint NOT NULL,
    seq integer,
    member_id bigint NOT NULL,
    arrived_at timestamp(6) without time zone,
    record_id character varying(36),
    verified boolean,
    reach_millis bigint,
    overtakes integer NOT NULL,
    forfeited boolean NOT NULL,
    verdict character varying(10),
    max_lead_meters bigint,
    max_trail_meters bigint,
    new_record boolean NOT NULL,
    active_member_id bigint,
    shown_gender character varying(6),
    valid_run_ordinal integer,
    CONSTRAINT ck_battle_participant_shown_gender CHECK (((shown_gender IS NULL) OR ((shown_gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying])::text[])))),
    CONSTRAINT ck_battle_participant_verdict CHECK (((verdict IS NULL) OR ((verdict)::text = ANY ((ARRAY['WIN'::character varying, 'LOSE'::character varying, 'DRAW'::character varying])::text[]))))
);


--
-- Name: battle_participant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.battle_participant ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.battle_participant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: celebrity_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.celebrity_record (
    id bigint NOT NULL,
    anonymous_name character varying(20) NOT NULL,
    distance_meters bigint NOT NULL,
    finish_millis bigint NOT NULL,
    splits text,
    source_name character varying(100) NOT NULL,
    source_url character varying(500),
    registered_at timestamp(6) without time zone NOT NULL,
    real_name_consented_at timestamp(6) without time zone,
    real_name_consent_basis character varying(200),
    figure_key character varying(20),
    CONSTRAINT ck_celebrity_record_positive CHECK (((distance_meters > 0) AND (finish_millis > 0))),
    CONSTRAINT ck_celebrity_record_real_name_consent CHECK ((((real_name_consented_at IS NULL) AND (real_name_consent_basis IS NULL)) OR ((real_name_consented_at IS NOT NULL) AND (real_name_consent_basis IS NOT NULL))))
);


--
-- Name: celebrity_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.celebrity_record ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.celebrity_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: coach_conversation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coach_conversation (
    conversation_id character varying(64) NOT NULL,
    member_id bigint NOT NULL,
    first_question character varying(80) NOT NULL,
    last_asked_at timestamp without time zone NOT NULL
);


--
-- Name: coach_daily_usage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coach_daily_usage (
    member_id bigint NOT NULL,
    usage_day date NOT NULL,
    answered_count integer NOT NULL,
    lease_token character varying(36),
    lease_until timestamp without time zone,
    CONSTRAINT coach_daily_usage_answered_count_check CHECK ((answered_count >= 0)),
    CONSTRAINT coach_daily_usage_check CHECK (((lease_token IS NULL) = (lease_until IS NULL)))
);


--
-- Name: content_report; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_report (
    id bigint NOT NULL,
    case_id bigint NOT NULL,
    reporter_id bigint,
    reason character varying(10) NOT NULL,
    detail character varying(200),
    reported_at timestamp(6) without time zone NOT NULL,
    resolved boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_content_report_reason CHECK (((reason)::text = ANY ((ARRAY['ABUSE'::character varying, 'SEXUAL'::character varying, 'SPAM'::character varying, 'PRIVACY'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: content_report_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.content_report ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.content_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: course_verdict; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course_verdict (
    record_id character varying(36) NOT NULL,
    member_id bigint NOT NULL,
    same_course boolean NOT NULL,
    judged_at timestamp(6) without time zone NOT NULL
);


--
-- Name: crew; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew (
    id bigint NOT NULL,
    name character varying(20) NOT NULL,
    intro character varying(40),
    tags character varying(200) NOT NULL,
    visibility character varying(10) NOT NULL,
    join_policy character varying(10) NOT NULL,
    logo_url character varying(500),
    invite_code character varying(6) NOT NULL,
    invite_code_expires_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_crew_join_policy CHECK (((join_policy)::text = ANY ((ARRAY['APPROVAL'::character varying, 'FREE'::character varying])::text[]))),
    CONSTRAINT ck_crew_visibility CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying])::text[])))
);


--
-- Name: crew_chat_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_chat_message (
    id bigint NOT NULL,
    crew_id bigint NOT NULL,
    author_id bigint,
    client_message_id character varying(36) NOT NULL,
    content character varying(500) NOT NULL,
    sent_at timestamp(6) without time zone NOT NULL,
    deleted boolean DEFAULT false NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    kind character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    run_distance_meters bigint,
    CONSTRAINT ck_crew_chat_message_kind CHECK (((kind)::text = ANY ((ARRAY['TEXT'::character varying, 'RUN_FINISHED'::character varying])::text[]))),
    CONSTRAINT ck_crew_chat_message_run_distance CHECK (((((kind)::text = 'TEXT'::text) AND (run_distance_meters IS NULL)) OR (((kind)::text = 'RUN_FINISHED'::text) AND (run_distance_meters IS NOT NULL) AND (run_distance_meters >= 0))))
);


--
-- Name: crew_chat_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_chat_message ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_chat_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_chat_reaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_chat_reaction (
    message_id bigint NOT NULL,
    member_id bigint NOT NULL,
    kind character varying(10) NOT NULL,
    reacted_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_crew_chat_reaction_kind CHECK (((kind)::text = ANY ((ARRAY['LIKE'::character varying, 'HEART'::character varying, 'CHECK'::character varying])::text[])))
);


--
-- Name: crew_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_logo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_logo (
    id bigint NOT NULL,
    uploader_id bigint NOT NULL,
    size_bytes integer NOT NULL,
    jpeg bytea NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_crew_logo_size CHECK (((size_bytes >= 1) AND (size_bytes <= 1048576)))
);


--
-- Name: crew_logo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_logo ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_logo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_manager_backup_v59; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_manager_backup_v59 (
    membership_id bigint,
    crew_id bigint,
    member_id bigint
);


--
-- Name: crew_meetup_attendance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_meetup_attendance (
    post_id bigint NOT NULL,
    member_id bigint NOT NULL,
    answer character varying(10) NOT NULL,
    answered_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_crew_meetup_attendance_answer CHECK (((answer)::text = ANY ((ARRAY['GOING'::character varying, 'NOT_GOING'::character varying, 'UNDECIDED'::character varying])::text[])))
);


--
-- Name: crew_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_membership (
    id bigint NOT NULL,
    crew_id bigint NOT NULL,
    member_id bigint NOT NULL,
    role character varying(10) NOT NULL,
    joined_at timestamp(6) without time zone NOT NULL,
    left_at timestamp(6) without time zone,
    leave_reason character varying(10),
    crew_intro character varying(40),
    CONSTRAINT ck_crew_membership_leave CHECK (((leave_reason IS NULL) OR ((leave_reason)::text = ANY ((ARRAY['WITHDREW'::character varying, 'KICKED'::character varying])::text[])))),
    CONSTRAINT ck_crew_membership_role CHECK (((role)::text = ANY ((ARRAY['LEADER'::character varying, 'MANAGER'::character varying, 'MEMBER'::character varying])::text[])))
);


--
-- Name: crew_membership_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_membership ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_membership_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_photo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_photo (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    crew_id bigint NOT NULL,
    seq smallint NOT NULL,
    size_bytes integer NOT NULL,
    jpeg bytea DEFAULT '\x'::bytea NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_crew_photo_seq CHECK (((seq >= 1) AND (seq <= 5))),
    CONSTRAINT ck_crew_photo_size CHECK (((size_bytes >= 0) AND (size_bytes <= 1048576)))
);


--
-- Name: crew_photo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_photo ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_photo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_poll_option; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_poll_option (
    post_id bigint NOT NULL,
    option_no smallint NOT NULL,
    label character varying(40) NOT NULL,
    CONSTRAINT ck_crew_poll_option_no CHECK (((option_no >= 1) AND (option_no <= 5)))
);


--
-- Name: crew_poll_response; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_poll_response (
    post_id bigint NOT NULL,
    member_id bigint NOT NULL,
    option_no smallint NOT NULL,
    responded_at timestamp(6) without time zone NOT NULL
);


--
-- Name: crew_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_post (
    id bigint NOT NULL,
    post_kind character varying(10) NOT NULL,
    crew_id bigint NOT NULL,
    author_id bigint,
    body character varying(1000) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted boolean NOT NULL,
    version bigint NOT NULL,
    scheduled_at timestamp(6) without time zone,
    distance character varying(10),
    vote_deadline timestamp(6) without time zone,
    status character varying(12),
    departed_at timestamp(6) without time zone,
    ended_at timestamp(6) without time zone,
    reminded boolean,
    hidden boolean DEFAULT false NOT NULL,
    pinned boolean,
    place_name character varying(50),
    capacity integer,
    poll_method character varying(10),
    poll_closes_at timestamp(6) without time zone,
    title character varying(200),
    series_id bigint,
    countdown_token character varying(40),
    countdown_planned_at timestamp(6) without time zone,
    countdown_start_at timestamp(6) without time zone,
    start_reason character varying(12),
    change_revision bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_crew_post_countdown CHECK (((countdown_token IS NULL) = (countdown_start_at IS NULL))),
    CONSTRAINT ck_crew_post_distance CHECK (((distance IS NULL) OR ((distance)::text = ANY ((ARRAY['KM1'::character varying, 'KM3'::character varying, 'KM5'::character varying, 'KM10'::character varying, 'HALF'::character varying])::text[])))),
    CONSTRAINT ck_crew_post_kind CHECK (((post_kind)::text = ANY ((ARRAY['CHEER'::character varying, 'CREW_RUN'::character varying, 'MEETUP'::character varying, 'NOTICE'::character varying, 'POLL'::character varying, 'BOARD'::character varying])::text[]))),
    CONSTRAINT ck_crew_post_meetup CHECK ((((post_kind)::text <> 'MEETUP'::text) OR ((scheduled_at IS NOT NULL) AND (place_name IS NOT NULL) AND ((capacity >= 2) AND (capacity <= 10))))),
    CONSTRAINT ck_crew_post_notice CHECK ((((post_kind)::text <> 'NOTICE'::text) OR (pinned IS NOT NULL))),
    CONSTRAINT ck_crew_post_poll CHECK ((((post_kind)::text <> 'POLL'::text) OR (((poll_method)::text = ANY ((ARRAY['YES_NO'::character varying, 'CHOICES'::character varying])::text[])) AND (poll_closes_at IS NOT NULL)))),
    CONSTRAINT ck_crew_post_start_reason CHECK (((start_reason IS NULL) OR ((start_reason)::text = ANY ((ARRAY['ALL_READY'::character varying, 'HOST'::character varying, 'SCHEDULED'::character varying])::text[])))),
    CONSTRAINT ck_crew_post_status CHECK (((status IS NULL) OR ((status)::text = ANY ((ARRAY['VOTING'::character varying, 'LOBBY_OPEN'::character varying, 'RUNNING'::character varying, 'FINISHED'::character varying, 'CANCELLED'::character varying])::text[]))))
);


--
-- Name: crew_post_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_post ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_post_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_run_participation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_run_participation (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    member_id bigint,
    voted_at timestamp(6) without time zone NOT NULL,
    answer character varying(10) NOT NULL,
    readiness character varying(12) NOT NULL,
    runner boolean NOT NULL,
    ended boolean NOT NULL,
    linked_record_id character varying(36),
    final_distance_m bigint,
    final_elapsed_ms bigint,
    countdown_candidate boolean DEFAULT false NOT NULL,
    joined_at timestamp(6) without time zone,
    measurement_record_id character varying(36),
    CONSTRAINT ck_crew_run_participation_answer CHECK (((answer)::text = ANY ((ARRAY['GOING'::character varying, 'NOT_GOING'::character varying, 'UNDECIDED'::character varying])::text[]))),
    CONSTRAINT ck_crew_run_participation_readiness CHECK (((readiness)::text = ANY ((ARRAY['NOT_ENTERED'::character varying, 'ENTERING'::character varying, 'READY'::character varying])::text[])))
);


--
-- Name: crew_run_participation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_run_participation ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_run_participation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: crew_run_series; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crew_run_series (
    id bigint NOT NULL,
    crew_id bigint NOT NULL,
    author_id bigint NOT NULL,
    last_post_id bigint NOT NULL,
    last_scheduled_at timestamp(6) without time zone NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    ended_at timestamp(6) without time zone,
    version bigint NOT NULL
);


--
-- Name: crew_run_series_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.crew_run_series ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.crew_run_series_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: daily_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.daily_plan (
    member_id bigint NOT NULL,
    plan_date date NOT NULL,
    distance_meters bigint NOT NULL,
    pace_seconds_per_km integer,
    CONSTRAINT ck_daily_plan_distance CHECK ((distance_meters > 0))
);


--
-- Name: disposal_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disposal_order (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    kind character varying(12) NOT NULL,
    due_at timestamp(6) without time zone NOT NULL,
    status character varying(20) NOT NULL,
    executed_at timestamp(6) without time zone,
    device_confirmed_at timestamp(6) without time zone,
    CONSTRAINT ck_disposal_order_kind CHECK (((kind)::text = ANY ((ARRAY['ROUTE'::character varying, 'HEART_RATE'::character varying])::text[]))),
    CONSTRAINT ck_disposal_order_status CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'EXECUTED'::character varying, 'DEVICE_CONFIRMED'::character varying])::text[])))
);


--
-- Name: disposal_order_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.disposal_order ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.disposal_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event (
    id bigint NOT NULL,
    slug character varying(40) NOT NULL,
    title character varying(60) NOT NULL,
    subtitle character varying(100) NOT NULL,
    kind character varying(20) NOT NULL,
    config text NOT NULL,
    starts_at timestamp(6) without time zone NOT NULL,
    ends_at timestamp(6) without time zone NOT NULL,
    banner_image_url character varying(300) NOT NULL,
    thumbnail_image_url character varying(300) NOT NULL,
    detail_url character varying(300) NOT NULL,
    sort_order integer NOT NULL,
    published boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_event_kind CHECK (((kind)::text = ANY ((ARRAY['COACH_CELEB'::character varying, 'RUN_DISTANCE'::character varying])::text[]))),
    CONSTRAINT ck_event_period CHECK ((ends_at > starts_at))
);


--
-- Name: event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.event ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: event_point_grant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_point_grant (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    event_id bigint NOT NULL,
    target_key character varying(40) NOT NULL,
    amount integer NOT NULL,
    granted_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_event_point_grant_amount CHECK ((amount > 0))
);


--
-- Name: event_point_grant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.event_point_grant ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.event_point_grant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: face_part_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.face_part_image (
    image_key character varying(200) NOT NULL,
    member_id bigint NOT NULL,
    part character varying(10) NOT NULL,
    png bytea NOT NULL,
    created_at timestamp(6) without time zone NOT NULL
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: friend_invite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.friend_invite (
    id bigint NOT NULL,
    inviter_member_id bigint NOT NULL,
    distance_meters bigint NOT NULL,
    token character varying(32) NOT NULL,
    status character varying(10) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    joiner_member_id bigint,
    battle_id bigint,
    closed_at timestamp(6) without time zone,
    version bigint NOT NULL,
    CONSTRAINT ck_friend_invite_accepted CHECK ((((status)::text <> 'ACCEPTED'::text) OR ((joiner_member_id IS NOT NULL) AND (battle_id IS NOT NULL)))),
    CONSTRAINT ck_friend_invite_distance CHECK ((distance_meters = ANY (ARRAY[(1000)::bigint, (3000)::bigint, (5000)::bigint, (10000)::bigint]))),
    CONSTRAINT ck_friend_invite_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: friend_invite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.friend_invite ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.friend_invite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: join_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.join_request (
    id bigint NOT NULL,
    crew_id bigint NOT NULL,
    member_id bigint NOT NULL,
    requested_at timestamp(6) without time zone NOT NULL,
    status character varying(10) NOT NULL,
    decided_at timestamp(6) without time zone,
    reason character varying(40),
    version bigint NOT NULL,
    CONSTRAINT ck_join_request_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'DISSOLVED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: join_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.join_request ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.join_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: live_battle_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.live_battle_session (
    battle_id bigint NOT NULL,
    snapshot text NOT NULL
);


--
-- Name: match_ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_ticket (
    member_id bigint NOT NULL,
    tier character varying(10) NOT NULL,
    distance character varying(10) NOT NULL,
    waiting_since timestamp(6) without time zone NOT NULL,
    status character varying(10) NOT NULL,
    assigned_at timestamp(6) without time zone,
    CONSTRAINT ck_match_ticket_status CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'ASSIGNED'::character varying])::text[])))
);


--
-- Name: member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member (
    id bigint NOT NULL,
    provider character varying(10),
    provider_account_id character varying(100),
    nickname character varying(20),
    birth_year integer,
    gender character varying(12),
    photo_url character varying(500),
    intro character varying(40),
    tier character varying(10) NOT NULL,
    level integer NOT NULL,
    joined_at timestamp(6) without time zone NOT NULL,
    unit character varying(5) NOT NULL,
    record_visibility character varying(10) NOT NULL,
    course_map_shared boolean NOT NULL,
    profile_visibility character varying(10) NOT NULL,
    muted_notification_types character varying(400) NOT NULL,
    watch_linked boolean NOT NULL,
    run_target character varying(10) NOT NULL,
    run_voice boolean NOT NULL,
    run_vibration boolean NOT NULL,
    run_follow_coach_plan boolean NOT NULL,
    battle_default_distance character varying(10) NOT NULL,
    battle_voice boolean NOT NULL,
    battle_vibration boolean NOT NULL,
    battle_gap_alert_interval character varying(10) NOT NULL,
    valid_run_count integer NOT NULL,
    recent_scores character varying(200) NOT NULL,
    total_valid_meters bigint NOT NULL,
    status character varying(10) NOT NULL,
    withdrawn_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    version bigint NOT NULL,
    account_type character varying(10) DEFAULT 'LINKED'::character varying NOT NULL,
    last_access_at timestamp(6) without time zone NOT NULL,
    guest_credential_hash character varying(64),
    screen_theme character varying(10) DEFAULT 'SYSTEM'::character varying NOT NULL,
    battle_proximity_alert boolean DEFAULT true NOT NULL,
    guide_seen boolean DEFAULT false NOT NULL,
    age_band_visibility character varying(10) DEFAULT 'PRIVATE'::character varying NOT NULL,
    gender_visibility character varying(10) DEFAULT 'PRIVATE'::character varying NOT NULL,
    CONSTRAINT ck_member_account CHECK (((((account_type)::text = 'GUEST'::text) AND (provider IS NULL) AND (provider_account_id IS NULL)) OR (((account_type)::text = 'LINKED'::text) AND (((status)::text = 'WITHDRAWN'::text) OR ((provider IS NOT NULL) AND (provider_account_id IS NOT NULL)))))),
    CONSTRAINT ck_member_account_type CHECK (((account_type)::text = ANY ((ARRAY['GUEST'::character varying, 'LINKED'::character varying])::text[]))),
    CONSTRAINT ck_member_age_band_visibility CHECK (((age_band_visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying])::text[]))),
    CONSTRAINT ck_member_gender_visibility CHECK (((gender_visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying])::text[]))),
    CONSTRAINT ck_member_record_visibility CHECK (((record_visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'CREW'::character varying, 'PRIVATE'::character varying])::text[]))),
    CONSTRAINT ck_member_screen_theme CHECK (((screen_theme)::text = ANY ((ARRAY['SYSTEM'::character varying, 'LIGHT'::character varying, 'DARK'::character varying])::text[]))),
    CONSTRAINT ck_member_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT ck_member_tier CHECK (((tier)::text = ANY ((ARRAY['UNRANKED'::character varying, 'BRONZE'::character varying, 'SILVER'::character varying, 'GOLD'::character varying, 'PLATINUM'::character varying, 'MASTER'::character varying])::text[]))),
    CONSTRAINT ck_member_withdrawn CHECK (((((status)::text = 'ACTIVE'::text) AND (withdrawn_at IS NULL)) OR (((status)::text = 'WITHDRAWN'::text) AND (withdrawn_at IS NOT NULL) AND (nickname IS NULL) AND (provider IS NULL) AND (provider_account_id IS NULL) AND (guest_credential_hash IS NULL))))
);


--
-- Name: member_consent; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_consent (
    member_id bigint NOT NULL,
    seq integer NOT NULL,
    item character varying(20) NOT NULL,
    agreed boolean NOT NULL,
    changed_at timestamp(6) without time zone NOT NULL,
    notice_version integer DEFAULT 1 NOT NULL,
    CONSTRAINT ck_member_consent_item CHECK (((item)::text = ANY ((ARRAY['AGE_14_PLUS'::character varying, 'TERMS'::character varying, 'PRIVACY'::character varying, 'LOCATION'::character varying, 'MARKETING'::character varying, 'HEART_RATE'::character varying, 'FACE_TRANSFER'::character varying, 'LLM_TRANSFER'::character varying])::text[]))),
    CONSTRAINT member_consent_notice_version_check CHECK ((notice_version > 0))
);


--
-- Name: member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.member ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    recipient_id bigint NOT NULL,
    type character varying(30) NOT NULL,
    content character varying(500) NOT NULL,
    occurred_at timestamp(6) without time zone NOT NULL,
    is_read boolean NOT NULL,
    target_screen character varying(30),
    target_id character varying(300),
    title character varying(60),
    CONSTRAINT ck_notification_type CHECK (((type)::text = ANY ((ARRAY['PLAN_MISSED'::character varying, 'BODY_SHAPE_CHANGED'::character varying, 'JOIN_REQUESTED'::character varying, 'JOIN_DECIDED'::character varying, 'JOIN_EXPIRED'::character varying, 'LEADER_TRANSFERRED'::character varying, 'CREW_DELETED'::character varying, 'KICKED'::character varying, 'CREW_RUN_RECRUIT'::character varying, 'CREW_RUN_SOON'::character varying, 'OPPONENT_ARRIVED'::character varying, 'BATTLE_CORRECTED'::character varying, 'CREW_NOTICE'::character varying, 'CREW_POLL'::character varying, 'POLL_CLOSING_SOON'::character varying, 'CREW_SCHEDULE'::character varying, 'CREW_CHAT'::character varying, 'CREW_GOAL_ACHIEVED'::character varying, 'MANAGER_CHANGED'::character varying, 'BACKGROUND_UNLOCKED'::character varying, 'REPORT_RESOLVED'::character varying, 'NOTICE'::character varying])::text[])))
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.notification ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: operation_setting; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operation_setting (
    setting_key character varying(40) NOT NULL,
    setting_value text NOT NULL,
    version bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL
);


--
-- Name: operation_setting_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operation_setting_history (
    id bigint NOT NULL,
    setting_key character varying(40) NOT NULL,
    before_value text NOT NULL,
    after_value text NOT NULL,
    changed_at timestamp(6) without time zone NOT NULL,
    operator_id character varying(100) NOT NULL
);


--
-- Name: operation_setting_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.operation_setting_history ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.operation_setting_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: owned_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.owned_item (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    item_id bigint NOT NULL,
    purchased_at timestamp(6) without time zone NOT NULL
);


--
-- Name: owned_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.owned_item ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.owned_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: personal_best; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.personal_best (
    member_id bigint NOT NULL,
    distance_key character varying(10) NOT NULL,
    record_id character varying(36),
    elapsed_millis bigint,
    distance_meters bigint,
    achieved_on date,
    has_unstable boolean,
    CONSTRAINT ck_personal_best_distance CHECK (((distance_key)::text = ANY ((ARRAY['KM1'::character varying, 'KM3'::character varying, 'KM5'::character varying, 'KM10'::character varying, 'HALF'::character varying, 'FULL'::character varying])::text[])))
);


--
-- Name: personal_record_book; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.personal_record_book (
    member_id bigint NOT NULL,
    longest_record_id character varying(36),
    longest_elapsed_millis bigint,
    longest_distance_meters bigint,
    longest_achieved_on date,
    longest_has_unstable boolean
);


--
-- Name: placement_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.placement_progress (
    member_id bigint NOT NULL,
    counted_rounds smallint DEFAULT 0 NOT NULL,
    provisional_tier character varying(10),
    confirmed_run_ordinal integer,
    completed_at timestamp without time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_placement_progress_rounds CHECK (((counted_rounds >= 0) AND (counted_rounds <= 3))),
    CONSTRAINT ck_placement_progress_tier CHECK (((provisional_tier IS NULL) OR ((provisional_tier)::text = ANY ((ARRAY['BRONZE'::character varying, 'SILVER'::character varying, 'GOLD'::character varying, 'PLATINUM'::character varying, 'MASTER'::character varying])::text[]))))
);


--
-- Name: placement_round; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.placement_round (
    member_id bigint NOT NULL,
    round_no smallint NOT NULL,
    battle_id bigint NOT NULL,
    score double precision NOT NULL,
    battle_distance character varying(10) NOT NULL,
    run_distance_meters bigint NOT NULL,
    run_elapsed_millis bigint NOT NULL,
    average_pace_seconds integer,
    verdict character varying(5) NOT NULL,
    valid_run_ordinal integer NOT NULL,
    counted_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_placement_round_no CHECK (((round_no >= 1) AND (round_no <= 3))),
    CONSTRAINT ck_placement_round_verdict CHECK (((verdict)::text = ANY ((ARRAY['WIN'::character varying, 'LOSE'::character varying, 'DRAW'::character varying])::text[])))
);


--
-- Name: push_device; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.push_device (
    token character varying(512) NOT NULL,
    member_id bigint NOT NULL,
    registered_at timestamp(6) without time zone NOT NULL
);


--
-- Name: race_contribution; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.race_contribution (
    id bigint NOT NULL,
    crew_id bigint NOT NULL,
    week_start date NOT NULL,
    member_id bigint,
    record_id character varying(36) NOT NULL,
    meters bigint NOT NULL,
    CONSTRAINT ck_race_contribution_meters CHECK ((meters >= 0))
);


--
-- Name: race_contribution_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.race_contribution ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.race_contribution_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ranking_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ranking_entry (
    snapshot_id bigint NOT NULL,
    entry_order integer NOT NULL,
    rank_no integer NOT NULL,
    subject_id bigint NOT NULL,
    display_name character varying(40) NOT NULL,
    tier character varying(10),
    distance_meters bigint NOT NULL
);


--
-- Name: ranking_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ranking_snapshot (
    id bigint NOT NULL,
    kind character varying(10) NOT NULL,
    period character varying(10) NOT NULL,
    computed_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_ranking_snapshot_kind CHECK (((kind)::text = ANY ((ARRAY['PERSONAL'::character varying, 'CREW'::character varying])::text[]))),
    CONSTRAINT ck_ranking_snapshot_period CHECK (((period)::text = ANY ((ARRAY['WEEKLY'::character varying, 'MONTHLY'::character varying])::text[])))
);


--
-- Name: ranking_snapshot_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ranking_snapshot ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.ranking_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: refresh_token_revocation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_token_revocation (
    jti character varying(36) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL
);


--
-- Name: report; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report (
    member_id bigint NOT NULL,
    from_run integer NOT NULL,
    to_run integer NOT NULL,
    average_score double precision NOT NULL,
    distance_change_meters bigint NOT NULL,
    pace_change_seconds integer NOT NULL,
    frequent_day character varying(10) NOT NULL,
    frequent_hour_band integer NOT NULL,
    frequency_change double precision NOT NULL,
    comment_text character varying(500),
    comment_source character varying(10),
    created_at timestamp(6) without time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT ck_report_comment_source CHECK (((comment_source)::text = ANY ((ARRAY['AI'::character varying, 'RULE'::character varying])::text[]))),
    CONSTRAINT ck_report_hour_band CHECK (((frequent_hour_band >= 0) AND (frequent_hour_band <= 7))),
    CONSTRAINT ck_report_runs CHECK (((from_run >= 1) AND (to_run >= from_run)))
);


--
-- Name: report_case; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_case (
    id bigint NOT NULL,
    target_kind character varying(15) NOT NULL,
    target_id bigint NOT NULL,
    crew_id bigint NOT NULL,
    status character varying(12) NOT NULL,
    open_reporter_count integer DEFAULT 0 NOT NULL,
    opened_at timestamp(6) without time zone NOT NULL,
    resolved_at timestamp(6) without time zone,
    resolver_id character varying(100),
    version bigint NOT NULL,
    CONSTRAINT ck_report_case_open_reporter_count CHECK ((open_reporter_count >= 0)),
    CONSTRAINT ck_report_case_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'AUTO_HIDDEN'::character varying, 'HIDDEN'::character varying, 'DISMISSED'::character varying])::text[]))),
    CONSTRAINT ck_report_case_target_kind CHECK (((target_kind)::text = ANY ((ARRAY['CHEER'::character varying, 'SCHEDULE'::character varying, 'NOTICE'::character varying, 'POLL'::character varying, 'BOARD'::character varying, 'CHAT_MESSAGE'::character varying])::text[])))
);


--
-- Name: report_case_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.report_case ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.report_case_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reward_accrual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reward_accrual (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    reason character varying(20) NOT NULL,
    source_id character varying(40) NOT NULL,
    amount integer NOT NULL,
    accrued_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_reward_accrual_amount CHECK ((amount > 0)),
    CONSTRAINT ck_reward_accrual_reason CHECK (((reason)::text = ANY ((ARRAY['VALID_RUN'::character varying, 'BATTLE_WIN'::character varying, 'EVENT'::character varying])::text[])))
);


--
-- Name: reward_accrual_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.reward_accrual ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.reward_accrual_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reward_wallet; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reward_wallet (
    member_id bigint NOT NULL,
    balance integer NOT NULL,
    version bigint NOT NULL,
    last_accrual_date date,
    last_accrual_date_amount integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_reward_wallet_balance CHECK ((balance >= 0)),
    CONSTRAINT ck_reward_wallet_last_amount CHECK ((last_accrual_date_amount >= 0))
);


--
-- Name: run_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.run_record (
    record_id character varying(36) NOT NULL,
    member_id bigint NOT NULL,
    started_at timestamp(6) without time zone NOT NULL,
    ended_at timestamp(6) without time zone NOT NULL,
    distance_meters bigint NOT NULL,
    elapsed_millis bigint NOT NULL,
    average_pace_seconds integer NOT NULL,
    mode character varying(10) NOT NULL,
    card_kind character varying(12) NOT NULL,
    battle_id bigint,
    crew_run_post_id bigint,
    valid boolean NOT NULL,
    judged boolean NOT NULL,
    score double precision,
    weather_temperature integer,
    weather_sky character varying(10),
    point_count integer NOT NULL,
    route_data bytea,
    route_purged boolean NOT NULL,
    unstable_segments text NOT NULL,
    heart_rate_data bytea,
    heart_rate_purged boolean NOT NULL,
    heart_rate_avg integer,
    heart_rate_max integer,
    new_records text NOT NULL,
    saved_at timestamp(6) without time zone NOT NULL,
    pauses text DEFAULT ''::text NOT NULL,
    paused_millis bigint DEFAULT 0 NOT NULL,
    tier_applied boolean DEFAULT false NOT NULL,
    excluded_meters bigint DEFAULT 0 NOT NULL,
    excluded_millis bigint DEFAULT 0 NOT NULL,
    rejudge_state character varying(8),
    route_shape text,
    evaluation_kind character varying(20),
    evaluation_tier character varying(10),
    evaluation_valid_runs integer,
    evaluation_runimal_type character varying(20),
    start_device character varying(5) DEFAULT 'PHONE'::character varying NOT NULL,
    motion_data bytea,
    look_gender character varying(6),
    look_top_item_id bigint,
    look_bottom_item_id bigint,
    look_shoes_item_id bigint,
    look_body_variant smallint,
    look_run_amount smallint,
    look_face_key character varying(255),
    CONSTRAINT ck_run_record_card_kind CHECK (((card_kind)::text = ANY ((ARRAY['FREE_RUN'::character varying, 'BATTLE_RUN'::character varying, 'SELF_RUN'::character varying, 'CELEB_RUN'::character varying, 'CREW_RUN'::character varying])::text[]))),
    CONSTRAINT ck_run_record_evaluation_all CHECK ((((evaluation_kind IS NULL) AND (evaluation_tier IS NULL) AND (evaluation_valid_runs IS NULL)) OR ((evaluation_kind IS NOT NULL) AND (evaluation_tier IS NOT NULL) AND (evaluation_valid_runs IS NOT NULL)))),
    CONSTRAINT ck_run_record_evaluation_kind CHECK (((evaluation_kind IS NULL) OR ((evaluation_kind)::text = ANY ((ARRAY['FIRST_PLACEMENT'::character varying, 'REEVALUATION'::character varying])::text[])))),
    CONSTRAINT ck_run_record_evaluation_valid_runs CHECK (((evaluation_valid_runs IS NULL) OR (evaluation_valid_runs >= 3))),
    CONSTRAINT ck_run_record_excluded CHECK (((excluded_meters >= 0) AND (excluded_millis >= 0))),
    CONSTRAINT ck_run_record_look CHECK ((((look_gender IS NULL) = (look_body_variant IS NULL)) AND ((look_gender IS NULL) = (look_run_amount IS NULL)) AND ((look_gender IS NOT NULL) OR ((look_top_item_id IS NULL) AND (look_bottom_item_id IS NULL) AND (look_shoes_item_id IS NULL) AND (look_face_key IS NULL))))),
    CONSTRAINT ck_run_record_mode CHECK (((mode)::text = ANY ((ARRAY['FREE'::character varying, 'BATTLE'::character varying, 'CREW_RUN'::character varying])::text[]))),
    CONSTRAINT ck_run_record_pause CHECK (((paused_millis >= 0) AND (((mode)::text = ANY ((ARRAY['FREE'::character varying, 'CREW_RUN'::character varying])::text[])) OR (paused_millis = 0)))),
    CONSTRAINT ck_run_record_period CHECK ((started_at < ended_at)),
    CONSTRAINT ck_run_record_positive CHECK (((distance_meters > 0) AND (elapsed_millis > 0))),
    CONSTRAINT ck_run_record_rejudge_state CHECK (((rejudge_state IS NULL) OR ((rejudge_state)::text = ANY ((ARRAY['PENDING'::character varying, 'VALID'::character varying, 'DONE'::character varying, 'SKIPPED'::character varying])::text[])))),
    CONSTRAINT ck_run_record_score CHECK (((valid AND (score IS NOT NULL)) OR ((NOT valid) AND (score IS NULL)))),
    CONSTRAINT ck_run_record_start_device CHECK (((start_device)::text = ANY ((ARRAY['PHONE'::character varying, 'WATCH'::character varying])::text[])))
);


--
-- Name: run_record_tombstone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.run_record_tombstone (
    record_id character varying(36) NOT NULL,
    member_id bigint NOT NULL,
    deleted_at timestamp(6) without time zone NOT NULL
);


--
-- Name: runbody_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.runbody_job (
    record_id character varying(36) NOT NULL,
    member_id bigint NOT NULL,
    status character varying(10) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    polls integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL,
    submitted_at timestamp without time zone,
    next_poll_at timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    overall_score integer,
    failure_reason character varying(40),
    run_started_at timestamp without time zone NOT NULL,
    CONSTRAINT runbody_job_overall_score_check CHECK (((overall_score >= 0) AND (overall_score <= 100))),
    CONSTRAINT runbody_job_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ANALYZING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: runimal_confirmed_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.runimal_confirmed_history (
    member_id bigint NOT NULL,
    type character varying(20) NOT NULL
);


--
-- Name: runimal_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.runimal_profile (
    member_id bigint NOT NULL,
    valid_run_count integer NOT NULL,
    provisional_type character varying(20),
    confirmed_type character varying(20),
    companion_ratio double precision,
    distance_ratio double precision,
    course_ratio double precision,
    habit_ratio double precision,
    judged_at timestamp(6) without time zone,
    version bigint NOT NULL,
    CONSTRAINT ck_runimal_profile_count CHECK ((valid_run_count >= 0))
);


--
-- Name: runimal_type_share; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.runimal_type_share (
    type character varying(20) NOT NULL,
    confirmed_count integer NOT NULL,
    total_confirmed integer NOT NULL,
    computed_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ck_runimal_type_share_count CHECK (((confirmed_count >= 0) AND (total_confirmed >= confirmed_count)))
);


--
-- Name: running_goal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.running_goal (
    member_id bigint NOT NULL,
    version bigint NOT NULL
);


--
-- Name: selfie_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.selfie_job (
    request_id character varying(64) NOT NULL,
    member_id bigint NOT NULL,
    job_id character varying(64),
    status character varying(16),
    head_id character varying(64),
    error_code character varying(100),
    error_message character varying(500),
    attempted_at timestamp without time zone NOT NULL,
    finished boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: virtual_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.virtual_record (
    id bigint NOT NULL,
    tier character varying(10) NOT NULL,
    distance character varying(10) NOT NULL,
    nickname character varying(20) NOT NULL,
    finish_millis bigint NOT NULL,
    CONSTRAINT ck_virtual_record_finish CHECK ((finish_millis > 0)),
    CONSTRAINT ck_virtual_record_tier CHECK (((tier)::text = ANY ((ARRAY['BRONZE'::character varying, 'SILVER'::character varying, 'GOLD'::character varying, 'PLATINUM'::character varying, 'MASTER'::character varying])::text[])))
);


--
-- Name: virtual_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.virtual_record ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.virtual_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: watch_session_generation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.watch_session_generation (
    member_id bigint NOT NULL,
    generation bigint NOT NULL,
    revoked_at timestamp(6) with time zone NOT NULL
);


--
-- Name: weekly_goal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.weekly_goal (
    member_id bigint NOT NULL,
    week_start date NOT NULL,
    run_count integer NOT NULL,
    distance_meters bigint NOT NULL,
    CONSTRAINT ck_weekly_goal_count CHECK (((run_count >= 1) AND (run_count <= 7))),
    CONSTRAINT ck_weekly_goal_distance CHECK ((distance_meters > 0))
);


--
-- Name: weekly_race; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.weekly_race (
    id bigint NOT NULL,
    crew_id bigint NOT NULL,
    week_start date NOT NULL,
    total_meters bigint NOT NULL,
    goal_meters bigint,
    goal_set_by bigint,
    achieved_at timestamp(6) without time zone,
    CONSTRAINT ck_weekly_race_achieved CHECK (((achieved_at IS NULL) OR (goal_meters IS NOT NULL))),
    CONSTRAINT ck_weekly_race_goal CHECK (((goal_meters IS NULL) OR (goal_meters >= 1000))),
    CONSTRAINT ck_weekly_race_total CHECK ((total_meters >= 0))
);


--
-- Name: weekly_race_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.weekly_race ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.weekly_race_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: analysis_event id; Type: DEFAULT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.analysis_event ALTER COLUMN id SET DEFAULT nextval('analytics.analysis_event_id_seq'::regclass);


--
-- Name: coach_message id; Type: DEFAULT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.coach_message ALTER COLUMN id SET DEFAULT nextval('analytics.coach_message_id_seq'::regclass);


--
-- Name: alembic_version alembic_version_pkc; Type: CONSTRAINT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.alembic_version
    ADD CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num);


--
-- Name: analysis_event analysis_event_pkey; Type: CONSTRAINT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.analysis_event
    ADD CONSTRAINT analysis_event_pkey PRIMARY KEY (id);


--
-- Name: coach_message coach_message_pkey; Type: CONSTRAINT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.coach_message
    ADD CONSTRAINT coach_message_pkey PRIMARY KEY (id);


--
-- Name: llm_model llm_model_pkey; Type: CONSTRAINT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.llm_model
    ADD CONSTRAINT llm_model_pkey PRIMARY KEY (model_id);


--
-- Name: session_result session_result_pkey; Type: CONSTRAINT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.session_result
    ADD CONSTRAINT session_result_pkey PRIMARY KEY (session_id);


--
-- Name: ai_purge_request ai_purge_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_purge_request
    ADD CONSTRAINT ai_purge_request_pkey PRIMARY KEY (member_id);


--
-- Name: audit_record audit_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_record
    ADD CONSTRAINT audit_record_pkey PRIMARY KEY (id);


--
-- Name: avatar_background avatar_background_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar_background
    ADD CONSTRAINT avatar_background_pkey PRIMARY KEY (member_id, landmark_key);


--
-- Name: avatar_equipment avatar_equipment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar_equipment
    ADD CONSTRAINT avatar_equipment_pkey PRIMARY KEY (member_id, slot);


--
-- Name: avatar_face_part avatar_face_part_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar_face_part
    ADD CONSTRAINT avatar_face_part_pkey PRIMARY KEY (member_id, part);


--
-- Name: avatar avatar_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar
    ADD CONSTRAINT avatar_pkey PRIMARY KEY (member_id);


--
-- Name: battle_disconnection battle_disconnection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_disconnection
    ADD CONSTRAINT battle_disconnection_pkey PRIMARY KEY (participant_id, seq);


--
-- Name: battle_participant battle_participant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_participant
    ADD CONSTRAINT battle_participant_pkey PRIMARY KEY (id);


--
-- Name: battle battle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle
    ADD CONSTRAINT battle_pkey PRIMARY KEY (id);


--
-- Name: celebrity_record celebrity_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.celebrity_record
    ADD CONSTRAINT celebrity_record_pkey PRIMARY KEY (id);


--
-- Name: coach_conversation coach_conversation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coach_conversation
    ADD CONSTRAINT coach_conversation_pkey PRIMARY KEY (conversation_id);


--
-- Name: coach_daily_usage coach_daily_usage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coach_daily_usage
    ADD CONSTRAINT coach_daily_usage_pkey PRIMARY KEY (member_id);


--
-- Name: content_report content_report_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_report
    ADD CONSTRAINT content_report_pkey PRIMARY KEY (id);


--
-- Name: course_verdict course_verdict_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_verdict
    ADD CONSTRAINT course_verdict_pkey PRIMARY KEY (record_id);


--
-- Name: crew_chat_message crew_chat_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_chat_message
    ADD CONSTRAINT crew_chat_message_pkey PRIMARY KEY (id);


--
-- Name: crew_chat_reaction crew_chat_reaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_chat_reaction
    ADD CONSTRAINT crew_chat_reaction_pkey PRIMARY KEY (message_id, member_id, kind);


--
-- Name: crew_logo crew_logo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_logo
    ADD CONSTRAINT crew_logo_pkey PRIMARY KEY (id);


--
-- Name: crew_membership crew_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_membership
    ADD CONSTRAINT crew_membership_pkey PRIMARY KEY (id);


--
-- Name: crew_photo crew_photo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_photo
    ADD CONSTRAINT crew_photo_pkey PRIMARY KEY (id);


--
-- Name: crew crew_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew
    ADD CONSTRAINT crew_pkey PRIMARY KEY (id);


--
-- Name: crew_post crew_post_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_post
    ADD CONSTRAINT crew_post_pkey PRIMARY KEY (id);


--
-- Name: crew_run_participation crew_run_participation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_run_participation
    ADD CONSTRAINT crew_run_participation_pkey PRIMARY KEY (id);


--
-- Name: crew_run_series crew_run_series_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_run_series
    ADD CONSTRAINT crew_run_series_pkey PRIMARY KEY (id);


--
-- Name: daily_plan daily_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_plan
    ADD CONSTRAINT daily_plan_pkey PRIMARY KEY (member_id, plan_date);


--
-- Name: disposal_order disposal_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_order
    ADD CONSTRAINT disposal_order_pkey PRIMARY KEY (id);


--
-- Name: event event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event
    ADD CONSTRAINT event_pkey PRIMARY KEY (id);


--
-- Name: event_point_grant event_point_grant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_point_grant
    ADD CONSTRAINT event_point_grant_pkey PRIMARY KEY (id);


--
-- Name: face_part_image face_part_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.face_part_image
    ADD CONSTRAINT face_part_image_pkey PRIMARY KEY (image_key);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: friend_invite friend_invite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_invite
    ADD CONSTRAINT friend_invite_pkey PRIMARY KEY (id);


--
-- Name: join_request join_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.join_request
    ADD CONSTRAINT join_request_pkey PRIMARY KEY (id);


--
-- Name: live_battle_session live_battle_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_battle_session
    ADD CONSTRAINT live_battle_session_pkey PRIMARY KEY (battle_id);


--
-- Name: match_ticket match_ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_ticket
    ADD CONSTRAINT match_ticket_pkey PRIMARY KEY (member_id);


--
-- Name: member_consent member_consent_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_consent
    ADD CONSTRAINT member_consent_pkey PRIMARY KEY (member_id, seq);


--
-- Name: member member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member
    ADD CONSTRAINT member_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: operation_setting_history operation_setting_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_setting_history
    ADD CONSTRAINT operation_setting_history_pkey PRIMARY KEY (id);


--
-- Name: operation_setting operation_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operation_setting
    ADD CONSTRAINT operation_setting_pkey PRIMARY KEY (setting_key);


--
-- Name: owned_item owned_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owned_item
    ADD CONSTRAINT owned_item_pkey PRIMARY KEY (id);


--
-- Name: personal_best personal_best_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_best
    ADD CONSTRAINT personal_best_pkey PRIMARY KEY (member_id, distance_key);


--
-- Name: personal_record_book personal_record_book_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_record_book
    ADD CONSTRAINT personal_record_book_pkey PRIMARY KEY (member_id);


--
-- Name: crew_meetup_attendance pk_crew_meetup_attendance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_meetup_attendance
    ADD CONSTRAINT pk_crew_meetup_attendance PRIMARY KEY (post_id, member_id);


--
-- Name: crew_poll_option pk_crew_poll_option; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_poll_option
    ADD CONSTRAINT pk_crew_poll_option PRIMARY KEY (post_id, option_no);


--
-- Name: crew_poll_response pk_crew_poll_response; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_poll_response
    ADD CONSTRAINT pk_crew_poll_response PRIMARY KEY (post_id, member_id);


--
-- Name: placement_progress placement_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.placement_progress
    ADD CONSTRAINT placement_progress_pkey PRIMARY KEY (member_id);


--
-- Name: placement_round placement_round_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.placement_round
    ADD CONSTRAINT placement_round_pkey PRIMARY KEY (member_id, round_no);


--
-- Name: push_device push_device_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_device
    ADD CONSTRAINT push_device_pkey PRIMARY KEY (token);


--
-- Name: race_contribution race_contribution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.race_contribution
    ADD CONSTRAINT race_contribution_pkey PRIMARY KEY (id);


--
-- Name: ranking_entry ranking_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ranking_entry
    ADD CONSTRAINT ranking_entry_pkey PRIMARY KEY (snapshot_id, entry_order);


--
-- Name: ranking_snapshot ranking_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ranking_snapshot
    ADD CONSTRAINT ranking_snapshot_pkey PRIMARY KEY (id);


--
-- Name: refresh_token_revocation refresh_token_revocation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token_revocation
    ADD CONSTRAINT refresh_token_revocation_pkey PRIMARY KEY (jti);


--
-- Name: report_case report_case_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_case
    ADD CONSTRAINT report_case_pkey PRIMARY KEY (id);


--
-- Name: report report_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_pkey PRIMARY KEY (member_id);


--
-- Name: reward_accrual reward_accrual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reward_accrual
    ADD CONSTRAINT reward_accrual_pkey PRIMARY KEY (id);


--
-- Name: reward_wallet reward_wallet_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reward_wallet
    ADD CONSTRAINT reward_wallet_pkey PRIMARY KEY (member_id);


--
-- Name: run_record run_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_record
    ADD CONSTRAINT run_record_pkey PRIMARY KEY (record_id);


--
-- Name: run_record_tombstone run_record_tombstone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.run_record_tombstone
    ADD CONSTRAINT run_record_tombstone_pkey PRIMARY KEY (record_id);


--
-- Name: runbody_job runbody_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.runbody_job
    ADD CONSTRAINT runbody_job_pkey PRIMARY KEY (record_id);


--
-- Name: runimal_confirmed_history runimal_confirmed_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.runimal_confirmed_history
    ADD CONSTRAINT runimal_confirmed_history_pkey PRIMARY KEY (member_id, type);


--
-- Name: runimal_profile runimal_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.runimal_profile
    ADD CONSTRAINT runimal_profile_pkey PRIMARY KEY (member_id);


--
-- Name: runimal_type_share runimal_type_share_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.runimal_type_share
    ADD CONSTRAINT runimal_type_share_pkey PRIMARY KEY (type);


--
-- Name: running_goal running_goal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.running_goal
    ADD CONSTRAINT running_goal_pkey PRIMARY KEY (member_id);


--
-- Name: selfie_job selfie_job_member_id_job_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selfie_job
    ADD CONSTRAINT selfie_job_member_id_job_id_key UNIQUE (member_id, job_id);


--
-- Name: selfie_job selfie_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selfie_job
    ADD CONSTRAINT selfie_job_pkey PRIMARY KEY (request_id);


--
-- Name: battle_participant uq_battle_participant_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_participant
    ADD CONSTRAINT uq_battle_participant_member UNIQUE (battle_id, member_id);


--
-- Name: crew uq_crew_invite_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew
    ADD CONSTRAINT uq_crew_invite_code UNIQUE (invite_code);


--
-- Name: crew uq_crew_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew
    ADD CONSTRAINT uq_crew_name UNIQUE (name);


--
-- Name: crew_photo uq_crew_photo_post_seq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_photo
    ADD CONSTRAINT uq_crew_photo_post_seq UNIQUE (post_id, seq);


--
-- Name: event_point_grant uq_event_point_grant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_point_grant
    ADD CONSTRAINT uq_event_point_grant UNIQUE (member_id, event_id, target_key);


--
-- Name: event uq_event_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event
    ADD CONSTRAINT uq_event_slug UNIQUE (slug);


--
-- Name: friend_invite uq_friend_invite_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.friend_invite
    ADD CONSTRAINT uq_friend_invite_token UNIQUE (token);


--
-- Name: member uq_member_account; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member
    ADD CONSTRAINT uq_member_account UNIQUE (provider, provider_account_id);


--
-- Name: member uq_member_nickname; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member
    ADD CONSTRAINT uq_member_nickname UNIQUE (nickname);


--
-- Name: owned_item uq_owned_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owned_item
    ADD CONSTRAINT uq_owned_item UNIQUE (member_id, item_id);


--
-- Name: placement_round uq_placement_round_battle; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.placement_round
    ADD CONSTRAINT uq_placement_round_battle UNIQUE (battle_id);


--
-- Name: race_contribution uq_race_contribution; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.race_contribution
    ADD CONSTRAINT uq_race_contribution UNIQUE (crew_id, week_start, record_id);


--
-- Name: report_case uq_report_case_target; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_case
    ADD CONSTRAINT uq_report_case_target UNIQUE (target_kind, target_id);


--
-- Name: reward_accrual uq_reward_accrual_source; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reward_accrual
    ADD CONSTRAINT uq_reward_accrual_source UNIQUE (member_id, reason, source_id);


--
-- Name: virtual_record uq_virtual_record_nickname; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.virtual_record
    ADD CONSTRAINT uq_virtual_record_nickname UNIQUE (nickname);


--
-- Name: weekly_race uq_weekly_race; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.weekly_race
    ADD CONSTRAINT uq_weekly_race UNIQUE (crew_id, week_start);


--
-- Name: virtual_record virtual_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.virtual_record
    ADD CONSTRAINT virtual_record_pkey PRIMARY KEY (id);


--
-- Name: watch_session_generation watch_session_generation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watch_session_generation
    ADD CONSTRAINT watch_session_generation_pkey PRIMARY KEY (member_id);


--
-- Name: weekly_goal weekly_goal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT weekly_goal_pkey PRIMARY KEY (member_id, week_start);


--
-- Name: weekly_race weekly_race_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.weekly_race
    ADD CONSTRAINT weekly_race_pkey PRIMARY KEY (id);


--
-- Name: ix_analysis_event_session; Type: INDEX; Schema: analytics; Owner: -
--

CREATE INDEX ix_analysis_event_session ON analytics.analysis_event USING btree (session_id);


--
-- Name: ix_coach_message_conversation; Type: INDEX; Schema: analytics; Owner: -
--

CREATE INDEX ix_coach_message_conversation ON analytics.coach_message USING btree (conversation_id, id);


--
-- Name: ix_coach_message_user_created; Type: INDEX; Schema: analytics; Owner: -
--

CREATE INDEX ix_coach_message_user_created ON analytics.coach_message USING btree (user_id, created_at);


--
-- Name: ix_session_result_user_done_started; Type: INDEX; Schema: analytics; Owner: -
--

CREATE INDEX ix_session_result_user_done_started ON analytics.session_result USING btree (user_id, started_at) WHERE (status = 'done'::text);


--
-- Name: ux_llm_model_active; Type: INDEX; Schema: analytics; Owner: -
--

CREATE UNIQUE INDEX ux_llm_model_active ON analytics.llm_model USING btree (active) WHERE active;


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_ai_purge_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_purge_due ON public.ai_purge_request USING btree (status, next_at);


--
-- Name: idx_audit_record_action_recorded_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_record_action_recorded_at ON public.audit_record USING btree (action, recorded_at);


--
-- Name: idx_audit_record_recorded_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_record_recorded_at ON public.audit_record USING btree (recorded_at);


--
-- Name: idx_battle_member_win_judged_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_member_win_judged_at ON public.battle USING btree (judged_at) WHERE (winner_member_id IS NOT NULL);


--
-- Name: idx_battle_participant_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_participant_member ON public.battle_participant USING btree (member_id);


--
-- Name: idx_battle_status_countdown; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_status_countdown ON public.battle USING btree (status, countdown_ends_at);


--
-- Name: idx_battle_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_status_created ON public.battle USING btree (status, created_at);


--
-- Name: idx_battle_status_max_ends; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_status_max_ends ON public.battle USING btree (status, max_ends_at);


--
-- Name: idx_battle_status_opponent_arrives; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_status_opponent_arrives ON public.battle USING btree (status, opponent_arrives_at);


--
-- Name: idx_battle_status_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_battle_status_start ON public.battle USING btree (status, start_at);


--
-- Name: idx_celebrity_record_distance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_celebrity_record_distance ON public.celebrity_record USING btree (distance_meters, registered_at DESC);


--
-- Name: idx_content_report_reporter; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_report_reporter ON public.content_report USING btree (reporter_id);


--
-- Name: idx_course_verdict_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_course_verdict_member ON public.course_verdict USING btree (member_id);


--
-- Name: idx_crew_chat_message_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_chat_message_author ON public.crew_chat_message USING btree (author_id);


--
-- Name: idx_crew_chat_message_crew_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_chat_message_crew_id ON public.crew_chat_message USING btree (crew_id, id DESC);


--
-- Name: idx_crew_chat_reaction_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_chat_reaction_member ON public.crew_chat_reaction USING btree (member_id);


--
-- Name: idx_crew_logo_uploader; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_logo_uploader ON public.crew_logo USING btree (uploader_id, created_at DESC);


--
-- Name: idx_crew_meetup_attendance_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_meetup_attendance_member ON public.crew_meetup_attendance USING btree (member_id);


--
-- Name: idx_crew_membership_crew; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_membership_crew ON public.crew_membership USING btree (crew_id);


--
-- Name: idx_crew_membership_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_membership_member ON public.crew_membership USING btree (member_id);


--
-- Name: idx_crew_photo_crew; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_photo_crew ON public.crew_photo USING btree (crew_id, id DESC);


--
-- Name: idx_crew_poll_response_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_poll_response_member ON public.crew_poll_response USING btree (member_id);


--
-- Name: idx_crew_post_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_author ON public.crew_post USING btree (author_id);


--
-- Name: idx_crew_post_crew_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_crew_created ON public.crew_post USING btree (crew_id, created_at DESC);


--
-- Name: idx_crew_post_crew_run_finished; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_crew_run_finished ON public.crew_post USING btree (crew_id, scheduled_at DESC) WHERE (((post_kind)::text = 'CREW_RUN'::text) AND ((status)::text = 'FINISHED'::text) AND (NOT deleted));


--
-- Name: idx_crew_post_crew_run_past; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_crew_run_past ON public.crew_post USING btree (crew_id, scheduled_at) WHERE (((post_kind)::text = 'CREW_RUN'::text) AND ((status)::text = ANY ((ARRAY['FINISHED'::character varying, 'CANCELLED'::character varying])::text[])));


--
-- Name: idx_crew_post_crew_run_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_crew_run_scheduled ON public.crew_post USING btree (crew_id, scheduled_at, id) WHERE (((post_kind)::text = 'CREW_RUN'::text) AND (NOT deleted));


--
-- Name: idx_crew_post_pinned_notice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_pinned_notice ON public.crew_post USING btree (crew_id) WHERE (((post_kind)::text = 'NOTICE'::text) AND pinned AND (NOT deleted));


--
-- Name: idx_crew_post_poll_closing; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_poll_closing ON public.crew_post USING btree (poll_closes_at) WHERE (((post_kind)::text = 'POLL'::text) AND (NOT deleted) AND (reminded = false));


--
-- Name: idx_crew_post_status_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_post_status_scheduled ON public.crew_post USING btree (status, scheduled_at);


--
-- Name: idx_crew_public_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_public_created ON public.crew USING btree (visibility, created_at DESC);


--
-- Name: idx_crew_run_participation_linked; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_run_participation_linked ON public.crew_run_participation USING btree (linked_record_id) WHERE (linked_record_id IS NOT NULL);


--
-- Name: idx_crew_run_participation_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_run_participation_member ON public.crew_run_participation USING btree (member_id);


--
-- Name: idx_crew_run_series_crew; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_run_series_crew ON public.crew_run_series USING btree (crew_id);


--
-- Name: idx_crew_run_series_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_crew_run_series_due ON public.crew_run_series USING btree (last_scheduled_at) WHERE active;


--
-- Name: idx_daily_plan_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_daily_plan_date ON public.daily_plan USING btree (plan_date);


--
-- Name: idx_disposal_order_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disposal_order_member ON public.disposal_order USING btree (member_id);


--
-- Name: idx_disposal_order_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disposal_order_scheduled ON public.disposal_order USING btree (status) WHERE ((status)::text = 'SCHEDULED'::text);


--
-- Name: idx_face_part_image_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_face_part_image_member ON public.face_part_image USING btree (member_id);


--
-- Name: idx_friend_invite_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_friend_invite_due ON public.friend_invite USING btree (expires_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_join_request_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_join_request_member ON public.join_request USING btree (member_id);


--
-- Name: idx_join_request_status_requested; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_join_request_status_requested ON public.join_request USING btree (status, requested_at);


--
-- Name: idx_match_ticket_waiting; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_ticket_waiting ON public.match_ticket USING btree (status, waiting_since);


--
-- Name: idx_member_idle_guest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_member_idle_guest ON public.member USING btree (last_access_at) WHERE (((account_type)::text = 'GUEST'::text) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: idx_member_tier_visibility; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_member_tier_visibility ON public.member USING btree (tier, record_visibility) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: idx_member_withdrawn_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_member_withdrawn_at ON public.member USING btree (withdrawn_at) WHERE ((status)::text = 'WITHDRAWN'::text);


--
-- Name: idx_notification_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_occurred ON public.notification USING btree (occurred_at);


--
-- Name: idx_notification_recipient_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_recipient_occurred ON public.notification USING btree (recipient_id, occurred_at DESC);


--
-- Name: idx_operation_setting_history_key_changed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_operation_setting_history_key_changed_at ON public.operation_setting_history USING btree (setting_key, changed_at);


--
-- Name: idx_push_device_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_push_device_member ON public.push_device USING btree (member_id);


--
-- Name: idx_race_contribution_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_race_contribution_member ON public.race_contribution USING btree (member_id);


--
-- Name: idx_race_contribution_record; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_race_contribution_record ON public.race_contribution USING btree (record_id);


--
-- Name: idx_ranking_snapshot_latest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ranking_snapshot_latest ON public.ranking_snapshot USING btree (kind, period, computed_at);


--
-- Name: idx_refresh_token_revocation_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_revocation_expires_at ON public.refresh_token_revocation USING btree (expires_at);


--
-- Name: idx_report_case_status_opened_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_case_status_opened_at ON public.report_case USING btree (status, opened_at);


--
-- Name: idx_reward_accrual_member_accrued; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_accrual_member_accrued ON public.reward_accrual USING btree (member_id, accrued_at);


--
-- Name: idx_reward_accrual_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reward_accrual_source ON public.reward_accrual USING btree (reason, source_id);


--
-- Name: idx_run_record_ghost; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_ghost ON public.run_record USING btree (member_id, distance_meters) WHERE (valid AND (NOT route_purged) AND (point_count > 0));


--
-- Name: idx_run_record_member_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_member_started ON public.run_record USING btree (member_id, started_at DESC);


--
-- Name: idx_run_record_member_valid_saved; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_member_valid_saved ON public.run_record USING btree (member_id, saved_at, record_id) WHERE valid;


--
-- Name: idx_run_record_member_valid_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_member_valid_started ON public.run_record USING btree (member_id, valid, started_at DESC);


--
-- Name: idx_run_record_rejudge; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_rejudge ON public.run_record USING btree (rejudge_state, saved_at, record_id) WHERE ((rejudge_state)::text = ANY ((ARRAY['PENDING'::character varying, 'VALID'::character varying])::text[]));


--
-- Name: idx_run_record_saved_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_saved_at ON public.run_record USING btree (saved_at);


--
-- Name: idx_run_record_tier_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_tier_pending ON public.run_record USING btree (saved_at) WHERE (valid AND (NOT tier_applied));


--
-- Name: idx_run_record_tombstone_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_tombstone_member ON public.run_record_tombstone USING btree (member_id);


--
-- Name: idx_run_record_valid_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_run_record_valid_started ON public.run_record USING btree (started_at, member_id) WHERE valid;


--
-- Name: idx_runbody_job_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_runbody_job_due ON public.runbody_job USING btree (status, next_poll_at);


--
-- Name: idx_runbody_job_member_started; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_runbody_job_member_started ON public.runbody_job USING btree (member_id, run_started_at DESC, record_id DESC);


--
-- Name: idx_virtual_record_tier_distance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_virtual_record_tier_distance ON public.virtual_record USING btree (tier, distance);


--
-- Name: ix_battle_placement; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_battle_placement ON public.battle USING btree (judged_at) WHERE (placement_round IS NOT NULL);


--
-- Name: ix_coach_conversation_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_coach_conversation_expiry ON public.coach_conversation USING btree (last_asked_at);


--
-- Name: ix_coach_conversation_member_asked; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_coach_conversation_member_asked ON public.coach_conversation USING btree (member_id, last_asked_at DESC);


--
-- Name: ix_selfie_job_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_selfie_job_pending ON public.selfie_job USING btree (member_id, finished);


--
-- Name: uq_battle_participant_active_member; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_battle_participant_active_member ON public.battle_participant USING btree (active_member_id);


--
-- Name: uq_content_report_reporter; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_content_report_reporter ON public.content_report USING btree (case_id, reporter_id) WHERE (reporter_id IS NOT NULL);


--
-- Name: uq_crew_chat_client; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_crew_chat_client ON public.crew_chat_message USING btree (author_id, client_message_id) WHERE (author_id IS NOT NULL);


--
-- Name: uq_crew_membership_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_crew_membership_active ON public.crew_membership USING btree (member_id) WHERE (left_at IS NULL);


--
-- Name: uq_crew_post_series_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_crew_post_series_scheduled ON public.crew_post USING btree (series_id, scheduled_at);


--
-- Name: uq_crew_run_participation; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_crew_run_participation ON public.crew_run_participation USING btree (post_id, member_id) WHERE (member_id IS NOT NULL);


--
-- Name: uq_friend_invite_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_friend_invite_pending ON public.friend_invite USING btree (inviter_member_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_join_request_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_join_request_pending ON public.join_request USING btree (crew_id, member_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_notification_unread_collapse; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_notification_unread_collapse ON public.notification USING btree (recipient_id, type, target_id) WHERE ((is_read = false) AND ((type)::text = 'CREW_CHAT'::text) AND (target_id IS NOT NULL));


--
-- Name: audit_record trg_audit_record_reject_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_record_reject_update BEFORE UPDATE ON public.audit_record FOR EACH ROW EXECUTE FUNCTION public.audit_record_reject_update();


--
-- Name: analysis_event analysis_event_session_id_fkey; Type: FK CONSTRAINT; Schema: analytics; Owner: -
--

ALTER TABLE ONLY analytics.analysis_event
    ADD CONSTRAINT analysis_event_session_id_fkey FOREIGN KEY (session_id) REFERENCES analytics.session_result(session_id) ON DELETE CASCADE;


--
-- Name: avatar_background avatar_background_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar_background
    ADD CONSTRAINT avatar_background_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.avatar(member_id) ON DELETE CASCADE;


--
-- Name: avatar_equipment avatar_equipment_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar_equipment
    ADD CONSTRAINT avatar_equipment_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.avatar(member_id) ON DELETE CASCADE;


--
-- Name: avatar_face_part avatar_face_part_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avatar_face_part
    ADD CONSTRAINT avatar_face_part_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.avatar(member_id) ON DELETE CASCADE;


--
-- Name: battle_disconnection battle_disconnection_participant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_disconnection
    ADD CONSTRAINT battle_disconnection_participant_id_fkey FOREIGN KEY (participant_id) REFERENCES public.battle_participant(id) ON DELETE CASCADE;


--
-- Name: battle_participant battle_participant_battle_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.battle_participant
    ADD CONSTRAINT battle_participant_battle_id_fkey FOREIGN KEY (battle_id) REFERENCES public.battle(id) ON DELETE CASCADE;


--
-- Name: coach_conversation coach_conversation_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coach_conversation
    ADD CONSTRAINT coach_conversation_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id) ON DELETE CASCADE;


--
-- Name: coach_daily_usage coach_daily_usage_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coach_daily_usage
    ADD CONSTRAINT coach_daily_usage_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id) ON DELETE CASCADE;


--
-- Name: content_report content_report_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_report
    ADD CONSTRAINT content_report_case_id_fkey FOREIGN KEY (case_id) REFERENCES public.report_case(id) ON DELETE CASCADE;


--
-- Name: crew_chat_message crew_chat_message_crew_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_chat_message
    ADD CONSTRAINT crew_chat_message_crew_id_fkey FOREIGN KEY (crew_id) REFERENCES public.crew(id) ON DELETE CASCADE;


--
-- Name: crew_chat_reaction crew_chat_reaction_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_chat_reaction
    ADD CONSTRAINT crew_chat_reaction_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.crew_chat_message(id) ON DELETE CASCADE;


--
-- Name: crew_meetup_attendance crew_meetup_attendance_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_meetup_attendance
    ADD CONSTRAINT crew_meetup_attendance_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.crew_post(id) ON DELETE CASCADE;


--
-- Name: crew_membership crew_membership_crew_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_membership
    ADD CONSTRAINT crew_membership_crew_id_fkey FOREIGN KEY (crew_id) REFERENCES public.crew(id) ON DELETE CASCADE;


--
-- Name: crew_photo crew_photo_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_photo
    ADD CONSTRAINT crew_photo_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.crew_post(id) ON DELETE CASCADE;


--
-- Name: crew_poll_option crew_poll_option_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_poll_option
    ADD CONSTRAINT crew_poll_option_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.crew_post(id) ON DELETE CASCADE;


--
-- Name: crew_post crew_post_series_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_post
    ADD CONSTRAINT crew_post_series_id_fkey FOREIGN KEY (series_id) REFERENCES public.crew_run_series(id);


--
-- Name: crew_run_participation crew_run_participation_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_run_participation
    ADD CONSTRAINT crew_run_participation_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.crew_post(id) ON DELETE CASCADE;


--
-- Name: daily_plan daily_plan_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.daily_plan
    ADD CONSTRAINT daily_plan_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.running_goal(member_id) ON DELETE CASCADE;


--
-- Name: event_point_grant event_point_grant_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_point_grant
    ADD CONSTRAINT event_point_grant_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.event(id) ON DELETE CASCADE;


--
-- Name: event_point_grant event_point_grant_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_point_grant
    ADD CONSTRAINT event_point_grant_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id) ON DELETE CASCADE;


--
-- Name: crew_poll_response fk_crew_poll_response_option; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crew_poll_response
    ADD CONSTRAINT fk_crew_poll_response_option FOREIGN KEY (post_id, option_no) REFERENCES public.crew_poll_option(post_id, option_no) ON DELETE CASCADE;


--
-- Name: member_consent member_consent_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_consent
    ADD CONSTRAINT member_consent_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: owned_item owned_item_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owned_item
    ADD CONSTRAINT owned_item_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.reward_wallet(member_id) ON DELETE CASCADE;


--
-- Name: personal_best personal_best_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_best
    ADD CONSTRAINT personal_best_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.personal_record_book(member_id) ON DELETE CASCADE;


--
-- Name: placement_progress placement_progress_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.placement_progress
    ADD CONSTRAINT placement_progress_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id) ON DELETE CASCADE;


--
-- Name: placement_round placement_round_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.placement_round
    ADD CONSTRAINT placement_round_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.placement_progress(member_id) ON DELETE CASCADE;


--
-- Name: ranking_entry ranking_entry_snapshot_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ranking_entry
    ADD CONSTRAINT ranking_entry_snapshot_id_fkey FOREIGN KEY (snapshot_id) REFERENCES public.ranking_snapshot(id) ON DELETE CASCADE;


--
-- Name: reward_accrual reward_accrual_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reward_accrual
    ADD CONSTRAINT reward_accrual_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.reward_wallet(member_id) ON DELETE CASCADE;


--
-- Name: selfie_job selfie_job_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.selfie_job
    ADD CONSTRAINT selfie_job_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id) ON DELETE CASCADE;


--
-- Name: weekly_goal weekly_goal_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.weekly_goal
    ADD CONSTRAINT weekly_goal_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.running_goal(member_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict OrIV6Vu7X0cEn498fWWxxAyuD2b1ndPPV2TDaLtv1syP2xnfIplCah7zftdyBax

