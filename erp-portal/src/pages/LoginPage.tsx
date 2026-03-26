import { FormEvent, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Center,
  Container,
  Divider,
  Group,
  Loader,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { useAuth } from '../auth/AuthProvider';

export function LoginPage() {
  const { t } = useTranslation();
  const { accessToken, login, sessionRestored, portalBootstrap, portalBootstrapLoaded } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/home';

  const [tenantSlugOrId, setTenantSlugOrId] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!portalBootstrapLoaded || !portalBootstrap?.tenantId) {
      return;
    }
    setTenantSlugOrId((prev) => (prev.trim() === '' ? portalBootstrap.tenantId! : prev));
  }, [portalBootstrapLoaded, portalBootstrap?.tenantId]);

  if (!sessionRestored || !portalBootstrapLoaded) {
    return (
      <Box component="main" mih="100dvh" bg="var(--mantine-color-gray-0)">
        <Stack gap={0} mih="100dvh">
          <Group justify="flex-end" px="md" pt="md" wrap="nowrap">
            <LanguageSwitcher size="sm" />
          </Group>
          <Center style={{ flex: 1 }} px="md">
            <Stack align="center" gap="md">
              <Loader size="md" type="dots" />
              <Text c="dimmed" size="sm">
                {t('login.checkingSession')}
              </Text>
            </Stack>
          </Center>
        </Stack>
      </Box>
    );
  }

  if (accessToken) {
    return <Navigate to={from} replace />;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      await login(tenantSlugOrId.trim(), email.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('login.loginFailed'));
    } finally {
      setPending(false);
    }
  }

  return (
    <Box component="main" mih="100dvh" bg="var(--mantine-color-gray-0)">
      <Stack gap={0} mih="100dvh">
        <Group justify="flex-end" px="md" pt="md" wrap="nowrap">
          <LanguageSwitcher size="sm" />
        </Group>
        <Center style={{ flex: 1 }} px="md" py="xl">
          <Container size={420} p={0}>
            <Paper shadow="sm" p="xl" radius="md" withBorder>
              <Title order={1} size="h3" fw={700} mb={4}>
                {t('appTitle')}
              </Title>
              <Text c="dimmed" size="sm" mb="xs">
                {t('login.signInTitle')}
              </Text>
              <Text c="dimmed" size="xs" mb="lg" lh={1.5}>
                {t('login.blurb')}
              </Text>
              <Divider mb="lg" />
              <form onSubmit={(e) => void onSubmit(e)}>
                <Stack gap="md">
                  <TextInput
                    label={t('login.tenantLabel')}
                    value={tenantSlugOrId}
                    onChange={(e) => setTenantSlugOrId(e.target.value)}
                    required
                    autoComplete="organization"
                    size="sm"
                  />
                  <TextInput
                    label={t('login.email')}
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoComplete="username"
                    size="sm"
                  />
                  <PasswordInput
                    label={t('login.password')}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    autoComplete="current-password"
                    size="sm"
                  />
                  {error && (
                    <Alert color="red" variant="light" title={t('login.signInFailed')} role="alert">
                      {error}
                    </Alert>
                  )}
                  <Button type="submit" loading={pending} fullWidth mt="xs">
                    {pending ? t('login.signingIn') : t('login.signIn')}
                  </Button>
                </Stack>
              </form>
            </Paper>
          </Container>
        </Center>
      </Stack>
    </Box>
  );
}
