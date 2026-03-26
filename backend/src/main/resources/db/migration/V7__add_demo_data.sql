DO $$
DECLARE
  new_id BIGINT;
BEGIN
  -- We use nextval so we never clash with current registrations
  SELECT nextval('user_seq') INTO new_id;
  
  INSERT INTO public.user (id, first_name, last_name, username, email, "password")
  VALUES (new_id, 'Demo', 'Recruiter', 'recruiter', 'recruiter@demo.com', '$2a$10$89RcEfrANkqDohZRV4m9ouS/SebEpZ7upOUso68BQriHRtdL7A33W');

  INSERT INTO public.user_role (user_id, role_id) VALUES(new_id, 1);
  INSERT INTO public.user_role (user_id, role_id) VALUES(new_id, 2);
END $$;
