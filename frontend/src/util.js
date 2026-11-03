import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export const money = (n) => currency.format(Number(n ?? 0));

export const formatDate = (iso) =>
  iso ? new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '';

export const formatDateTime = (iso) => (iso ? new Date(iso).toLocaleString() : '');

export const today = () => new Date().toLocaleDateString('en-CA');

/** "LARGE_PURCHASE" -> "Large purchase" */
export const label = (value) =>
  value ? value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ') : '';

export const EXPENSE_CATEGORIES = ['BILLS', 'GROCERIES', 'TRANSPORTATION', 'ENTERTAINMENT', 'LARGE_PURCHASE'];
export const BILL_TYPES = ['UTILITIES', 'RENT', 'LOAN', 'TRANSPORTATION_PAYMENT', 'SUBSCRIPTION'];
export const RECURRENCES = ['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'];

/** Loads data from a GET endpoint and exposes a reload function. */
export function useApi(path) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const reload = useCallback(() => {
    setError('');
    return api(path).then(setData).catch((e) => setError(e.message));
  }, [path]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { data, error, reload };
}
