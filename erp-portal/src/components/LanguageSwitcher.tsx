import { Select } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { FALLBACK_LOCALE } from '../i18n/constants';

const OPTIONS = [
  { value: 'en', labelKey: 'language.en' as const },
  { value: 'es', labelKey: 'language.es' as const },
];

type Props = {
  size?: 'xs' | 'sm' | 'md';
};

export function LanguageSwitcher({ size = 'xs' }: Props) {
  const { t, i18n } = useTranslation();
  const lng = (i18n.language || FALLBACK_LOCALE).split(/[-_]/)[0] || FALLBACK_LOCALE;

  return (
    <Select
      size={size}
      w={130}
      aria-label={t('language.label')}
      data={OPTIONS.map((o) => ({ value: o.value, label: t(o.labelKey) }))}
      value={OPTIONS.some((o) => o.value === lng) ? lng : FALLBACK_LOCALE}
      onChange={(v) => {
        if (v) void i18n.changeLanguage(v);
      }}
    />
  );
}
