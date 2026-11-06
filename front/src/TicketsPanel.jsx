import { useEffect, useRef, useState } from 'react'
import {
  AlertCircle, ArrowLeft, ArrowRight, BarChart3, CalendarDays, Check,
  ChevronRight, Clock3, Database, FileText, Headset, LoaderCircle,
  Paperclip, Plus, RefreshCw, Search, ShieldCheck, Tag, UserRound,
} from 'lucide-react'
import { api } from './api'
import CreateTicketForm from './CreateTicketForm'
import './tickets.css'

const PAGE_SIZE = 10
const STATUSES = [
  { code: 'NEW', label: 'Новое', tone: 'new' },
  { code: 'IN_PROGRESS', label: 'В работе', tone: 'progress' },
  { code: 'RESOLVED', label: 'Решено', tone: 'resolved' },
  { code: 'CLOSED', label: 'Закрыто', tone: 'closed' },
]

function ticketId(ticket) {
  return ticket?._id ?? ticket?.id
}

function dateTimestamp(value) {
  if (value == null) return NaN
  return new Date(typeof value === 'number' && value < 1e11 ? value * 1000 : value).getTime()
}

function dateLabel(value) {
  const date = new Date(dateTimestamp(value))
  if (Number.isNaN(date.getTime())) return 'Дата не указана'
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function fileSize(value) {
  if (value == null || !Number.isFinite(Number(value)) || Number(value) < 0) return 'Размер не указан'
  const bytes = Number(value)
  const units = ['Б', 'КБ', 'МБ', 'ГБ']
  const unit = bytes === 0 ? 0 : Math.min(Math.floor(Math.log(bytes) / Math.log(1000)), units.length - 1)
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(bytes / 1000 ** unit) + ' ' + units[unit]
}

function analyticsValues(data) {
  if (!data || typeof data !== 'object') throw new Error('Сервис вернул некорректную аналитику')
  return Object.fromEntries(STATUSES.map(({ code }) => {
    const value = Array.isArray(data.rows) ? data.rows.find((row) => row.key === code)?.value : data[code]
    if (typeof value === 'number' && Number.isFinite(value)) return [code, value]
    if (value && Number.isFinite(value.sum) && Number.isFinite(value.count) && value.count > 0) {
      return [code, value.sum / value.count]
    }
    return [code, null]
  }))
}

function StatusBadge({ status }) {
  const item = STATUSES.find((value) => value.code === status)
  return (
    <span className={'ticket-status ' + (item?.tone || 'closed')}>
      <span aria-hidden="true" />{status || 'Не указан'}
    </span>
  )
}

function ErrorNotice({ message, retry }) {
  return (
    <div className="ticket-error" role="alert">
      <AlertCircle size={18} />
      <span>{message}</span>
      {retry && <button className="icon-button" onClick={retry} aria-label="Повторить загрузку"><RefreshCw size={16} /></button>}
    </div>
  )
}

export default function TicketsPanel({ session, showToast, onError, onConnectionChange }) {
  const [query, setQuery] = useState({ mode: 'all', tag: '', fromDate: '', skip: 0 })
  const [filters, setFilters] = useState(() => ({ tag: query.tag, fromDate: query.fromDate }))
  const [tickets, setTickets] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [analytics, setAnalytics] = useState(null)
  const [analyticsVersion, setAnalyticsVersion] = useState(0)
  const [detailVersion, setDetailVersion] = useState(0)
  const [searchLoading, setSearchLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [analyticsLoading, setAnalyticsLoading] = useState(true)
  const [searchError, setSearchError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [analyticsError, setAnalyticsError] = useState('')
  const [updating, setUpdating] = useState(false)
  const [migrating, setMigrating] = useState(false)
  const [tags, setTags] = useState([])
  const [tagsLoading, setTagsLoading] = useState(true)
  const [tagsError, setTagsError] = useState('')
  const [tagsVersion, setTagsVersion] = useState(0)
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [creationError, setCreationError] = useState('')
  const selection = useRef(selectedId)
  selection.current = selectedId
  const busy = updating || migrating || saving
  const authorized = Boolean(session?.token)

  const visibleTickets = query.mode === 'all' ? tickets.slice(query.skip, query.skip + PAGE_SIZE) : tickets
  const hasNextPage = query.mode === 'all' ? query.skip + PAGE_SIZE < tickets.length : tickets.length === PAGE_SIZE

  useEffect(() => {
    const controller = new AbortController()
    setTagsLoading(true)
    setTagsError('')
    api.getTicketTags(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        if (!Array.isArray(data) || !data.every((tag) => typeof tag === 'string' && tag.length > 0)) {
          throw new Error('Сервис вернул некорректный список меток')
        }
        setTags([...new Set(data)])
        onConnectionChange(false)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setTags([])
        setTagsError(error.message || 'Не удалось загрузить метки')
        onError(error, 'Не удалось загрузить метки')
      })
      .finally(() => {
        if (!controller.signal.aborted) setTagsLoading(false)
      })
    return () => controller.abort()
  }, [tagsVersion, onError, onConnectionChange])

  useEffect(() => {
    const controller = new AbortController()
    setSearchLoading(true)
    setSearchError('')
    const load = query.mode === 'all'
      ? api.getTickets(controller.signal)
      : api.searchTickets(query.tag, new Date(query.fromDate + 'T00:00:00').toISOString(), PAGE_SIZE, query.skip, controller.signal)
    load
      .then((data) => {
        if (controller.signal.aborted) return
        if (!Array.isArray(data)) throw new Error('Сервис вернул некорректный список обращений')
        const sorted = query.mode === 'all'
          ? [...data].sort((left, right) => (dateTimestamp(right.createdAt) || 0) - (dateTimestamp(left.createdAt) || 0))
          : data
        const page = query.mode === 'all' ? sorted.slice(query.skip, query.skip + PAGE_SIZE) : sorted
        setTickets(sorted)
        setSelectedId((current) => page.some((ticket) => ticketId(ticket) === current) ? current : ticketId(page[0]) ?? null)
        onConnectionChange(false)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setTickets([])
        setSelectedId(null)
        setSearchError(error.message || 'Не удалось загрузить обращения')
        onError(error, 'Не удалось загрузить обращения')
      })
      .finally(() => {
        if (!controller.signal.aborted) setSearchLoading(false)
      })
    return () => controller.abort()
  }, [query, onError, onConnectionChange])

  useEffect(() => {
    const controller = new AbortController()
    setAnalyticsLoading(true)
    setAnalyticsError('')
    api.getAnalytics(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        setAnalytics(analyticsValues(data))
        onConnectionChange(false)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setAnalyticsError(error.message || 'Не удалось загрузить аналитику')
        onError(error, 'Не удалось загрузить аналитику')
      })
      .finally(() => {
        if (!controller.signal.aborted) setAnalyticsLoading(false)
      })
    return () => controller.abort()
  }, [analyticsVersion, onError, onConnectionChange])

  useEffect(() => {
    setDetail(null)
    setDetailError('')
    if (!selectedId) {
      setDetailLoading(false)
      return
    }
    const controller = new AbortController()
    setDetailLoading(true)
    api.getTicket(selectedId, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        if (!data || ticketId(data) !== selectedId) throw new Error('Сервис вернул некорректное обращение')
        setDetail(data)
        onConnectionChange(false)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setDetailError(error.status === 404 ? 'Обращение не найдено' : error.message || 'Не удалось открыть обращение')
        onError(error, 'Не удалось открыть обращение')
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false)
      })
    return () => controller.abort()
  }, [selectedId, detailVersion, onError, onConnectionChange])

  function search(event) {
    event.preventDefault()
    if (!tags.includes(filters.tag) || !filters.fromDate || busy || tagsLoading || tagsError) return
    setQuery({ mode: 'search', tag: filters.tag, fromDate: filters.fromDate, skip: 0 })
  }

  function showAll() {
    setFilters({ tag: '', fromDate: '' })
    setQuery({ mode: 'all', tag: '', fromDate: '', skip: 0 })
  }

  async function createTicket(payload) {
    if (!authorized) return showToast('Сначала войдите как оператор', 'error')
    if (busy) return
    setSaving(true)
    setCreationError('')
    try {
      const created = await api.createTicket(payload, session.token)
      if (!ticketId(created)) throw new Error('Сервис вернул некорректное обращение')
      setSelectedId(ticketId(created))
      setDetail(created)
      setCreating(false)
      showAll()
      setDetailVersion((current) => current + 1)
      setAnalyticsVersion((current) => current + 1)
      onConnectionChange(false)
      showToast('Обращение создано')
    } catch (error) {
      setCreationError(error.message || 'Не удалось создать обращение')
      onError(error, 'Не удалось создать обращение')
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(status) {
    if (!authorized) return showToast('Сначала войдите как оператор', 'error')
    if (!selectedId || busy) return
    const id = selectedId
    setUpdating(true)
    try {
      const updated = await api.updateTicketStatus(id, status, session.token)
      if (selection.current === id) setDetail(updated)
      setTickets((current) => current.map((ticket) => ticketId(ticket) === id ? updated : ticket))
      setAnalyticsVersion((current) => current + 1)
      onConnectionChange(false)
      showToast('Статус обращения обновлён')
    } catch (error) {
      onError(error, 'Не удалось изменить статус')
    } finally {
      setUpdating(false)
    }
  }

  async function migrate() {
    if (!authorized) return showToast('Сначала войдите как оператор', 'error')
    if (busy) return
    setMigrating(true)
    try {
      const result = await api.migrateTickets(session.token)
      showToast(typeof result === 'string' ? result : result?.message || 'Миграция завершена')
      setQuery((current) => ({ ...current }))
      setDetailVersion((current) => current + 1)
      setAnalyticsVersion((current) => current + 1)
      onConnectionChange(false)
    } catch (error) {
      onError(error, 'Не удалось выполнить миграцию')
    } finally {
      setMigrating(false)
    }
  }

  return (
    <section className="tickets-section" aria-labelledby="tickets-title">
      <header className="support-heading">
        <div>
          <span className="eyebrow"><Headset size={14} /> Обращения клиентов и курьеров</span>
          <h1 id="tickets-title">Служба поддержки</h1>
          <p>Каждое обращение — под контролем. От первого сообщения до решения.</p>
        </div>
        <div className="support-actions">
          <button className="button primary new-ticket-button" disabled={!authorized || busy || creating}
            title={!authorized ? 'Войдите как оператор' : undefined}
            onClick={() => { setCreationError(''); setCreating(true) }}>
            <Plus size={17} /> Новое обращение
          </button>
          {!authorized && <span className="auth-hint">Для создания войдите как оператор.</span>}
        </div>
      </header>

      <section className="ticket-analytics card" aria-labelledby="analytics-title" aria-busy={analyticsLoading}>
        <div className="analytics-intro">
          <span className="analytics-icon"><BarChart3 size={23} /></span>
          <span className="eyebrow">Аналитика обращений</span>
          <h2 id="analytics-title">Среднее количество действий</h2>
          <p>Смены состояний на одно обращение</p>
          <button className="analytics-refresh" onClick={() => setAnalyticsVersion((current) => current + 1)}
            disabled={analyticsLoading || busy}>
            <RefreshCw size={13} className={analyticsLoading ? 'spin' : ''} />
            {analyticsLoading ? 'Обновляем' : 'Обновить'}
          </button>
        </div>
        <div className="analytics-values">
          {STATUSES.map(({ code, label, tone }) => (
            <div key={code} className={'analytics-value ' + tone}>
              <span className="analytics-code">{code}</span>
              <strong>{analytics?.[code] == null ? '—' : analytics[code].toFixed(1)}</strong>
              <small>{label}</small>
            </div>
          ))}
        </div>
        {analyticsError && <ErrorNotice message={analyticsError} retry={() => setAnalyticsVersion((current) => current + 1)} />}
      </section>

      <div className="tickets-grid">
        <aside className="ticket-search-panel card">
          <div className="panel-heading">
            <div><span className="eyebrow">Поиск обращений</span><h2>Найти тикет</h2></div>
            <span className="heading-icon coral"><Search size={19} /></span>
          </div>
          <form className="ticket-search-form" onSubmit={search}>
            <label htmlFor="ticket-tag">Метка (Tag)</label>
            <div className="ticket-input">
              <Tag size={16} />
              <select id="ticket-tag" value={filters.tag}
                onChange={(event) => setFilters((current) => ({ ...current, tag: event.target.value }))}
                required disabled={busy || tagsLoading || Boolean(tagsError)}>
                <option value="" disabled>{tagsLoading ? 'Загружаем метки…' : 'Выберите метку'}</option>
                {tags.map((tag) => <option key={tag} value={tag}>{tag}</option>)}
              </select>
            </div>
            <label htmlFor="ticket-from-date">Дата создания от</label>
            <div className="ticket-input">
              <CalendarDays size={16} />
              <input id="ticket-from-date" type="date" min="0001-01-01" max="9999-12-31" value={filters.fromDate}
                onChange={(event) => setFilters((current) => ({ ...current, fromDate: event.target.value }))}
                required disabled={busy} />
            </div>
            <button className="button primary" disabled={searchLoading || busy || tagsLoading || Boolean(tagsError) || !tags.includes(filters.tag) || !filters.fromDate}>
              {searchLoading ? <LoaderCircle className="spin" size={16} /> : <Search size={16} />} Найти
            </button>
            <button type="button" className="button ticket-reset" onClick={showAll}
              disabled={searchLoading || busy}>Показать все обращения</button>
          </form>
          {tagsError && <ErrorNotice message={tagsError} retry={() => setTagsVersion((current) => current + 1)} />}

          <div className="ticket-results-heading">
            <strong>{query.mode === 'all' ? 'Все обращения' : 'Результаты поиска'}</strong>
            <span>{searchLoading ? 'Загрузка…' : query.mode === 'all' ? 'Всего: ' + tickets.length : tickets.length + ' на странице'}</span>
          </div>
          {searchError ? <ErrorNotice message={searchError} retry={() => setQuery((current) => ({ ...current }))} /> : (
            <div className="ticket-results" aria-busy={searchLoading}>
              {searchLoading ? <Loading label="Ищем обращения" /> : tickets.length === 0 ? (
                <Empty icon={Search} title="Обращений не найдено" text={query.mode === 'all' ? 'Создайте первое обращение с помощью кнопки выше.' : 'Попробуйте другую метку или более раннюю дату.'} />
              ) : visibleTickets.map((ticket) => (
                <button key={ticketId(ticket)}
                  className={'ticket-row' + (selectedId === ticketId(ticket) ? ' active' : '')}
                  aria-pressed={selectedId === ticketId(ticket)}
                  disabled={busy || creating} onClick={() => setSelectedId(ticketId(ticket))}>
                  <span className="ticket-row-top"><StatusBadge status={ticket.status} /><ChevronRight size={16} /></span>
                  <strong>{ticket.title || 'Без заголовка'}</strong>
                  <time>{dateLabel(ticket.createdAt)}</time>
                </button>
              ))}
            </div>
          )}
          <div className="ticket-pagination">
            <button className="icon-button bordered" aria-label="Предыдущая страница"
              disabled={query.skip === 0 || searchLoading || busy}
              onClick={() => setQuery((current) => ({ ...current, skip: Math.max(0, current.skip - PAGE_SIZE) }))}>
              <ArrowLeft size={16} />
            </button>
            <span>Страница {Math.floor(query.skip / PAGE_SIZE) + 1}</span>
            <button className="icon-button bordered" aria-label="Следующая страница"
              disabled={!hasNextPage || searchLoading || busy || Boolean(searchError)}
              onClick={() => setQuery((current) => ({ ...current, skip: current.skip + PAGE_SIZE }))}>
              <ArrowRight size={16} />
            </button>
          </div>
          <div className="storage-note"><Database size={16} /><span><strong>CouchDB · tickets</strong><small>Поиск по метке и дате создания</small></span></div>
        </aside>

        <div className="ticket-work-area">
          <article className="ticket-detail card" aria-busy={detailLoading}>
            {creating ? (
              <CreateTicketForm tags={tags} tagsLoading={tagsLoading} tagsError={tagsError}
                onRetryTags={() => setTagsVersion((current) => current + 1)}
                authorized={authorized} saving={saving} error={creationError}
                onCreate={createTicket} onCancel={() => setCreating(false)}
                onError={onError} onConnectionChange={onConnectionChange} />
            ) : detailLoading ? <Loading label="Открываем обращение" /> : detailError ? (
              <ErrorNotice message={detailError} retry={() => setDetailVersion((current) => current + 1)} />
            ) : detail ? (
              <TicketDetail ticket={detail} authorized={authorized} busy={busy || searchLoading}
                updating={updating} onChangeStatus={changeStatus} />
            ) : <Empty icon={FileText} title="Выберите обращение" text="Здесь появятся описание, автор, вложения и управление статусом." />}
          </article>

          <section className="ticket-migration card" aria-labelledby="migration-title">
            <div className="migration-copy">
              <span className="migration-icon"><Database size={21} /></span>
              <div>
                <span className="eyebrow">Управление данными</span>
                <h2 id="migration-title">Миграция структуры</h2>
                <p>Заменить вложенного пользователя ссылкой userId в старых обращениях.</p>
              </div>
            </div>
            <button className="button migration-button" onClick={migrate}
              disabled={!authorized || busy || creating || searchLoading} title={!authorized ? 'Войдите как оператор' : undefined}>
              {migrating ? <LoaderCircle size={16} className="spin" /> : <RefreshCw size={16} />}
              {migrating ? 'Выполняется миграция…' : 'Запустить миграцию БД (v1 -> v2)'}
            </button>
            {!authorized && <p className="auth-hint"><ShieldCheck size={14} /> Для запуска нужна сессия оператора.</p>}
          </section>
        </div>
      </div>
    </section>
  )
}

function TicketDetail({ ticket, authorized, busy, updating, onChangeStatus }) {
  const [nextStatus, setNextStatus] = useState(ticket.status || 'NEW')
  useEffect(() => setNextStatus(ticket.status || 'NEW'), [ticket.status, ticket._id, ticket.id])
  const attachments = Array.isArray(ticket.attachments) ? ticket.attachments : []
  const tags = Array.isArray(ticket.tags) ? ticket.tags : []
  const author = ticket.userId || ticket.embeddedUser?.fullName || ticket.embeddedUser?.username || 'Автор не указан'

  return (
    <>
      <div className="ticket-detail-top">
        <span className="eyebrow"><FileText size={14} /> Карточка обращения</span>
        <StatusBadge status={ticket.status} />
      </div>
      <h2 className="ticket-title">{ticket.title || 'Без заголовка'}</h2>
      <div className="ticket-meta">
        <span><Clock3 size={14} /> {dateLabel(ticket.createdAt)}</span>
        <span><RefreshCw size={14} /> Изменений: {ticket.changeCount ?? 0}</span>
      </div>
      <p className="ticket-description">{ticket.description || 'Описание не добавлено.'}</p>
      <div className="ticket-tags" aria-label="Метки обращения">
        {tags.length ? tags.map((tag, index) => <span key={tag + index}><Tag size={12} />{tag}</span>) : <small>Меток нет</small>}
      </div>
      <div className="ticket-author">
        <span className="ticket-author-icon"><UserRound size={20} /></span>
        <div><small>Автор обращения</small><strong>{author}</strong></div>
        <span className={'schema-badge' + (ticket.userId ? '' : ' legacy')}>{ticket.userId ? 'userId · v2' : ticket.embeddedUser ? 'embeddedUser · v1' : '—'}</span>
      </div>
      <section className="ticket-attachments" aria-labelledby="attachments-title">
        <h3 id="attachments-title"><Paperclip size={16} /> Вложения <span>{attachments.length}</span></h3>
        {attachments.length ? <ul>{attachments.map((attachment, index) => (
          <li key={(attachment.fileName || 'file') + index}>
            <span className="attachment-icon"><FileText size={18} /></span>
            <span><strong>{attachment.fileName || 'Без имени'}</strong>
              <small>{fileSize(attachment.fileSize)} · {attachment.contentType || 'Тип не указан'}</small>
            </span>
          </li>
        ))}</ul> : <p className="ticket-muted">К обращению не добавлены файлы.</p>}
      </section>
      <form className="ticket-status-form" onSubmit={(event) => {
        event.preventDefault()
        onChangeStatus(nextStatus)
      }}>
        <label htmlFor="ticket-status">Статус обращения</label>
        <div>
          <select id="ticket-status" value={nextStatus} onChange={(event) => setNextStatus(event.target.value)}
            disabled={!authorized || busy}>
            {!STATUSES.some((status) => status.code === ticket.status) && ticket.status && (
              <option value={ticket.status}>{ticket.status}</option>
            )}
            {STATUSES.map(({ code, label }) => <option key={code} value={code}>{code} — {label}</option>)}
          </select>
          <button className="button dark" disabled={!authorized || busy || nextStatus === ticket.status}>
            {updating ? <LoaderCircle size={16} className="spin" /> : <Check size={16} />} Сохранить статус
          </button>
        </div>
        {!authorized && <p className="auth-hint"><ShieldCheck size={14} /> Войдите как оператор, чтобы менять статус.</p>}
      </form>
    </>
  )
}

function Loading({ label }) {
  return <div className="ticket-loading" role="status"><LoaderCircle size={23} className="spin" /><span>{label}</span></div>
}

function Empty({ icon: Icon, title, text }) {
  return <div className="ticket-empty"><span><Icon size={26} /></span><h3>{title}</h3><p>{text}</p></div>
}
