INSERT INTO public.user (id, first_name, last_name, username, email, "password")
VALUES (4, 'Demo', 'Recruiter', 'recruiter', 'recruiter@demo.com', '$2a$10$89RcEfrANkqDohZRV4m9ouS/SebEpZ7upOUso68BQriHRtdL7A33W');

INSERT INTO public.user_role (user_id, role_id) VALUES(4, 1);
INSERT INTO public.user_role (user_id, role_id) VALUES(4, 2);

SELECT setval('user_seq', max(id)) FROM public.user;
