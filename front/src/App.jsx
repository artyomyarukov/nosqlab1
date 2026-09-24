import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Activity,
  AlertCircle,
  ArrowRight,
  Bell,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Database,
  Edit3,
  Eye,
  LoaderCircle,
  LogIn,
  LogOut,
  MapPin,
  PackageCheck,
  Phone,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react'
import { API_URL, api } from './api'

const STORAGE_KEY = 'food-delivery-operator-session'
const noticePresets = [
  'Ваш заказ задерживается на 10 минут',
  'Курьер прибыл в ресторан',
  'Заказ успешно передан курьеру',
]

function readStoredSession() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY))
  } catch {
    return null
  }
}

function formatRemaining(milliseconds) {
  const total = Math.max(0, Math.floor(milliseconds / 1000))
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

function roleLabel(role) {
  return role === 'COURIER' ? 'Курьер' : role === 'CLIENT' ? 'Клиент' : role
}

function App() {
  const [session, setSession] = useState(readStoredSession)
  const [username, setUsername] = useState('operator1')
  const [remaining, setRemaining] = useState(0)
  const [users, setUsers] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [profile, setProfile] = useState(null)
  const [history, setHistory] = useState([])
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState({ fullName: '', phone: '', deliveryAddress: '' })
  const [notification, setNotification] = useState({ message: '', type: 'INFO' })
  const [loading, setLoading] = useState({ users: true, profile: false, history: false, auth: false, action: false })
  const [toast, setToast] = useState(null)
  const [apiOffline, setApiOffline] = useState(false)

  const showToast = useCallback((message, tone = 'success') => {
    setToast({ message, tone })
    window.setTimeout(() => setToast(null), 3600)
  }, [])

  const clearSession = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    setSession(null)
    setRemaining(0)
  }, [])

  const handleError = useCallback((error, fallback) => {
    if (error?.status === 401) clearSession()
    if (!error?.status) setApiOffline(true)
    showToast(error?.message || fallback, 'error')
  }, [clearSession, showToast])

  const loadUsers = useCallback(async () => {
    setLoading((state) => ({ ...state, users: true }))
    try {
      const data = await api.users()
      setUsers(data)
      setApiOffline(false)
      setSelectedId((current) => current ?? data[0]?.id ?? null)
    } catch (error) {
      handleError(error, 'Не удалось загрузить пользователей')
    } finally {
      setLoading((state) => ({ ...state, users: false }))
    }
  }, [handleError])

  const loadHistory = useCallback(async (id, quiet = false) => {
    if (!id) return
    if (!quiet) setLoading((state) => ({ ...state, history: true }))
    try {
      setHistory(await api.history(id))
    } catch (error) {
      handleError(error, 'Не удалось загрузить историю')
    } finally {
      setLoading((state) => ({ ...state, history: false }))
    }
  }, [handleError])

  const loadProfile = useCallback(async (id) => {
    if (!id) return
    setLoading((state) => ({ ...state, profile: true }))
    try {
      const data = await api.profile(id)
      setProfile(data)
      setEditForm({
        fullName: data.fullName || '',
        phone: data.phone || '',
        deliveryAddress: data.deliveryAddress || '',
      })
      setApiOffline(false)
    } catch (error) {
      setProfile(null)
      handleError(error, 'Не удалось загрузить профиль')
    } finally {
      setLoading((state) => ({ ...state, profile: false }))
    }
  }, [handleError])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  useEffect(() => {
    if (!selectedId) return
    setEditing(false)
    Promise.all([loadProfile(selectedId), loadHistory(selectedId)])
  }, [selectedId, loadHistory, loadProfile])

  useEffect(() => {
    if (!session?.token) return
    api.session(session.token).then((status) => {
      setSession((current) => ({ ...current, ...status }))
    }).catch(clearSession)
  }, []) // validate a restored token once

  useEffect(() => {
    if (!session?.expiresAt) return
    const tick = () => {
      const next = new Date(session.expiresAt).getTime() - Date.now()
      setRemaining(Math.max(0, next))
      if (next <= 0) clearSession()
    }
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [session?.expiresAt, clearSession])

  const filteredUsers = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) return users
    return users.filter((user) =>
      [user.fullName, user.username, user.role].some((value) => value?.toLowerCase().includes(needle)),
    )
  }, [query, users])

  async function login(event) {
    event.preventDefault()
    if (!username.trim()) return
    setLoading((state) => ({ ...state, auth: true }))
    try {
      const data = await api.login(username.trim())
      const nextSession = { ...data, username: username.trim() }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession))
      setSession(nextSession)
      setApiOffline(false)
      showToast(`Добро пожаловать, ${data.operatorName}`)
    } catch (error) {
      handleError(error, 'Не удалось войти')
    } finally {
      setLoading((state) => ({ ...state, auth: false }))
    }
  }

  async function logout() {
    try {
      await api.logout(session?.token)
    } catch {
      // Local logout should remain available when the backend is unreachable.
    } finally {
      clearSession()
      showToast('Сессия завершена', 'neutral')
    }
  }

  async function saveProfile(event) {
    event.preventDefault()
    if (!session) return showToast('Сначала войдите как оператор', 'error')
    setLoading((state) => ({ ...state, action: true }))
    try {
      await api.updateProfile(selectedId, editForm, session.token)
      setEditing(false)
      await Promise.all([loadProfile(selectedId), loadHistory(selectedId, true)])
      showToast('Профиль обновлён, кэш Riak инвалидирован')
    } catch (error) {
      handleError(error, 'Не удалось обновить профиль')
    } finally {
      setLoading((state) => ({ ...state, action: false }))
    }
  }

  async function sendNotification(event) {
    event.preventDefault()
    if (!session) return showToast('Сначала войдите как оператор', 'error')
    if (!notification.message.trim()) return
    setLoading((state) => ({ ...state, action: true }))
    try {
      await api.notify(selectedId, { ...notification, message: notification.message.trim() }, session.token)
      setNotification((current) => ({ ...current, message: '' }))
      await loadHistory(selectedId, true)
      showToast('Уведомление отправлено и добавлено в историю')
    } catch (error) {
      handleError(error, 'Не удалось отправить уведомление')
    } finally {
      setLoading((state) => ({ ...state, action: false }))
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="FoodDelivery">
          <span className="brand-mark"><PackageCheck size={25} strokeWidth={2.2} /></span>
          <span><strong>FoodDelivery</strong><small>Operator Panel</small></span>
        </a>

        <div className="environment-pill">
          <span className={`status-dot ${apiOffline ? 'offline' : ''}`} />
          <span>{apiOffline ? 'API недоступен' : 'Система работает'}</span>
          <code>{API_URL.replace('http://', '')}</code>
        </div>

        {session ? (
          <div className="operator-session">
            <span className="avatar">АО</span>
            <span className="operator-copy"><small>Оператор</small><strong>{session.operatorName}</strong></span>
            <span className="session-time"><Clock3 size={14} /> {formatRemaining(remaining)}</span>
            <button className="icon-button" onClick={logout} title="Выйти"><LogOut size={18} /></button>
          </div>
        ) : (
          <form className="login-form" onSubmit={login}>
            <label>
              <span>Логин оператора</span>
              <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="operator1" />
            </label>
            <button className="button primary compact" disabled={loading.auth}>
              {loading.auth ? <LoaderCircle className="spin" size={17} /> : <LogIn size={17} />} Войти
            </button>
          </form>
        )}
      </header>

      <main id="top" className="dashboard">
        <section className="hero-strip">
          <div>
            <span className="eyebrow"><Activity size={14} /> Центр поддержки и логистики</span>
            <h1>Добрый вечер{session ? `, ${session.operatorName.split(' ')[0]}` : ''}</h1>
            <p>Профили, уведомления и история действий — в одном рабочем пространстве.</p>
          </div>
          <div className="hero-metrics">
            <div><UsersRound size={19} /><span><strong>{users.length}</strong><small>пользователя</small></span></div>
            <div><ShieldCheck size={19} /><span><strong>{session ? 'Активна' : 'Не активна'}</strong><small>сессия Riak</small></span></div>
          </div>
        </section>

        {apiOffline && (
          <div className="offline-banner">
            <AlertCircle size={20} />
            <div><strong>Нет соединения с бэкендом</strong><span>Запустите Spring Boot на порту 8080 и повторите запрос.</span></div>
            <button className="button ghost compact" onClick={loadUsers}><RefreshCw size={16} /> Повторить</button>
          </div>
        )}

        <div className="workspace-grid">
          <aside className="users-panel card">
            <div className="panel-heading">
              <div><span className="eyebrow">Пользователи</span><h2>Клиенты и курьеры</h2></div>
              <span className="count-badge">{filteredUsers.length}</span>
            </div>
            <div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Имя, логин или роль" /></div>
            <div className="users-list">
              {loading.users ? <PanelLoader label="Загружаем пользователей" /> : filteredUsers.map((user) => (
                <button key={user.id} className={`user-row ${selectedId === user.id ? 'active' : ''}`} onClick={() => setSelectedId(user.id)}>
                  <span className={`user-icon ${user.role?.toLowerCase()}`}><UserRound size={20} /></span>
                  <span className="user-main"><strong>{user.fullName}</strong><small>@{user.username}</small></span>
                  <span className={`role-label ${user.role?.toLowerCase()}`}>{roleLabel(user.role)}</span>
                  <ChevronRight size={17} />
                </button>
              ))}
              {!loading.users && filteredUsers.length === 0 && <EmptyState icon={Search} title="Ничего не найдено" text="Попробуйте изменить запрос." />}
            </div>
            <div className="storage-note"><Database size={17} /><span><strong>Источник списка</strong><small>PostgreSQL · таблица users</small></span></div>
          </aside>

          <section className="work-area">
            {!selectedId ? (
              <div className="card empty-workspace"><UsersRound size={32} /><h2>Выберите пользователя</h2><p>Профиль и история появятся здесь.</p></div>
            ) : (
              <>
                <ProfileCard profile={profile} loading={loading.profile} editing={editing} setEditing={setEditing} editForm={editForm} setEditForm={setEditForm} saveProfile={saveProfile} actionLoading={loading.action} isAuthorized={Boolean(session)} refresh={() => loadProfile(selectedId)} />

                <div className="lower-grid">
                  <NotificationCard notification={notification} setNotification={setNotification} send={sendNotification} loading={loading.action} isAuthorized={Boolean(session)} />
                  <HistoryCard history={history} loading={loading.history} refresh={() => loadHistory(selectedId)} />
                </div>
              </>
            )}
          </section>
        </div>
      </main>

      {toast && <div className={`toast ${toast.tone}`}>{toast.tone === 'error' ? <AlertCircle size={19} /> : <CheckCircle2 size={19} />}<span>{toast.message}</span><button onClick={() => setToast(null)}><X size={16} /></button></div>}
    </div>
  )
}

function ProfileCard({ profile, loading, editing, setEditing, editForm, setEditForm, saveProfile, actionLoading, isAuthorized, refresh }) {
  if (loading) return <div className="card profile-card"><PanelLoader label="Загружаем профиль и проверяем кэш" /></div>
  if (!profile) return <div className="card profile-card"><EmptyState icon={UserRound} title="Профиль недоступен" text="Проверьте соединение с API." /></div>

  return (
    <article className="card profile-card">
      <div className="profile-header">
        <div className="large-avatar">{profile.fullName?.split(' ').slice(0, 2).map((part) => part[0]).join('') || '??'}</div>
        <div className="profile-title"><span className="eyebrow">Профиль пользователя · #{profile.id}</span><h2>{profile.fullName}</h2><p>@{profile.username}</p></div>
        <span className={`role-chip ${profile.role?.toLowerCase()}`}><UserRound size={15} /> {roleLabel(profile.role)}</span>
        <div className="profile-actions">
          <button className="icon-button bordered" onClick={refresh} title="Открыть профиль ещё раз"><RefreshCw size={17} /></button>
          <button className="button dark compact" onClick={() => setEditing(true)} disabled={!isAuthorized}><Edit3 size={16} /> Редактировать</button>
        </div>
      </div>

      <div className="lab-indicators">
        <div className={`indicator ${profile.fromCache ? 'cache' : 'postgres'}`}>
          <span className="indicator-icon"><Database size={21} /></span>
          <span><small>Источник данных</small><strong>{profile.fromCache ? 'Кэш Riak KV' : 'PostgreSQL'}</strong><em>{profile.fromCache ? 'CACHE HIT · быстро' : 'CACHE MISS · кэш заполнен'}</em></span>
          <Check size={18} />
        </div>
        <div className="indicator visits">
          <span className="indicator-icon"><Eye size={21} /></span>
          <span><small>CRDT PN-Counter</small><strong>{profile.visitCount} просмотров</strong><em>Атомарный счётчик Riak</em></span>
          <Sparkles size={18} />
        </div>
      </div>

      {editing ? (
        <form className="edit-form" onSubmit={saveProfile}>
          <Field label="ФИО" value={editForm.fullName} onChange={(value) => setEditForm({ ...editForm, fullName: value })} icon={UserRound} required />
          <Field label="Телефон" value={editForm.phone} onChange={(value) => setEditForm({ ...editForm, phone: value })} icon={Phone} required />
          <Field label="Адрес доставки" value={editForm.deliveryAddress} onChange={(value) => setEditForm({ ...editForm, deliveryAddress: value })} icon={MapPin} required wide />
          <div className="form-actions"><button type="button" className="button ghost" onClick={() => setEditing(false)}>Отмена</button><button className="button primary" disabled={actionLoading}>{actionLoading ? <LoaderCircle className="spin" size={17} /> : <Check size={17} />} Сохранить изменения</button></div>
        </form>
      ) : (
        <div className="contact-grid">
          <div><span><Phone size={16} /> Телефон</span><strong>{profile.phone || 'Не указан'}</strong></div>
          <div><span><MapPin size={16} /> Адрес доставки</span><strong>{profile.deliveryAddress || 'Не указан'}</strong></div>
        </div>
      )}
    </article>
  )
}

function NotificationCard({ notification, setNotification, send, loading, isAuthorized }) {
  return (
    <article className="card notification-card">
      <div className="panel-heading"><div><span className="eyebrow">Связь с пользователем</span><h2>Новое уведомление</h2></div><span className="heading-icon coral"><Bell size={19} /></span></div>
      <form onSubmit={send}>
        <label className="textarea-label"><span>Сообщение</span><textarea rows="4" value={notification.message} onChange={(event) => setNotification({ ...notification, message: event.target.value })} placeholder="Введите текст уведомления…" maxLength="240" /><small>{notification.message.length}/240</small></label>
        <div className="presets">{noticePresets.map((preset) => <button type="button" key={preset} onClick={() => setNotification({ ...notification, message: preset })}>{preset}</button>)}</div>
        <div className="notification-footer">
          <div className="type-switch">{['INFO', 'WARNING', 'SUCCESS'].map((type) => <button type="button" key={type} className={notification.type === type ? 'active' : ''} onClick={() => setNotification({ ...notification, type })}>{type}</button>)}</div>
          <button className="button primary" disabled={!isAuthorized || !notification.message.trim() || loading}>{loading ? <LoaderCircle className="spin" size={17} /> : <Send size={17} />} Отправить</button>
        </div>
        {!isAuthorized && <p className="auth-hint"><ShieldCheck size={15} /> Войдите как оператор, чтобы отправлять уведомления.</p>}
      </form>
    </article>
  )
}

function HistoryCard({ history, loading, refresh }) {
  return (
    <article className="card history-card">
      <div className="panel-heading"><div><span className="eyebrow">Журнал аудита</span><h2>История действий</h2></div><button className="icon-button bordered" onClick={refresh} title="Обновить историю"><RefreshCw size={17} /></button></div>
      <div className="timeline">
        {loading ? <PanelLoader label="Загружаем историю" /> : history.length ? history.map((event, index) => (
          <div className="timeline-item" key={`${event.timestamp}-${index}`}>
            <span className={`timeline-dot ${event.action?.includes('NOTIFICATION') ? 'notification' : ''}`}>{event.action?.includes('NOTIFICATION') ? <Bell size={14} /> : <Edit3 size={14} />}</span>
            <div className="timeline-content"><div><strong>{event.action || 'Действие'}</strong><time>{formatDate(event.timestamp)}</time></div><p>{event.details || 'Без описания'}</p><span><UserRound size={13} /> {event.operator || 'Неизвестный оператор'}</span></div>
          </div>
        )) : <EmptyState icon={Clock3} title="История пока пуста" text="Отправьте уведомление или измените профиль." />}
      </div>
    </article>
  )
}

function Field({ label, value, onChange, icon: Icon, wide, required }) {
  return <label className={wide ? 'wide' : ''}><span>{label}</span><div className="input-with-icon"><Icon size={16} /><input value={value} onChange={(event) => onChange(event.target.value)} required={required} /></div></label>
}

function PanelLoader({ label }) {
  return <div className="panel-loader"><LoaderCircle className="spin" size={25} /><span>{label}</span></div>
}

function EmptyState({ icon: Icon, title, text }) {
  return <div className="empty-state"><Icon size={25} /><strong>{title}</strong><p>{text}</p></div>
}

export default App
