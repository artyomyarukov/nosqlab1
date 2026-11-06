import { useEffect, useState } from 'react'
import { AlertCircle, LoaderCircle, Plus, RefreshCw, ShieldCheck, X } from 'lucide-react'
import { api } from './api'

export default function CreateTicketForm({
  tags, tagsLoading, tagsError, onRetryTags, authorized, saving, error,
  onCreate, onCancel, onError, onConnectionChange,
}) {
  const [draft, setDraft] = useState({ userId: '', title: '', description: '', tags: [] })
  const [users, setUsers] = useState([])
  const [usersLoading, setUsersLoading] = useState(true)
  const [usersError, setUsersError] = useState('')
  const [usersVersion, setUsersVersion] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setUsersLoading(true)
    setUsersError('')
    api.users(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        if (!Array.isArray(data)) throw new Error('Сервис вернул некорректный список пользователей')
        setUsers(data)
        onConnectionChange(false)
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setUsers([])
        setUsersError(error.message || 'Не удалось загрузить пользователей')
        onError(error, 'Не удалось загрузить пользователей')
      })
      .finally(() => {
        if (!controller.signal.aborted) setUsersLoading(false)
      })
    return () => controller.abort()
  }, [usersVersion, onError, onConnectionChange])

  const canSubmit = authorized && !saving && !usersLoading && !usersError && !tagsLoading && !tagsError
    && users.some((user) => String(user.id) === draft.userId)
    && draft.title.trim() && draft.description.trim()
    && draft.tags.every((tag) => tags.includes(tag))

  function changeField(event) {
    const { name, value } = event.target
    setDraft((current) => ({ ...current, [name]: value }))
  }

  function submit(event) {
    event.preventDefault()
    if (!canSubmit) return
    onCreate({ ...draft, title: draft.title.trim(), description: draft.description.trim() })
  }

  return (
    <form className="ticket-create-form" onSubmit={submit} aria-busy={saving}>
      <div className="ticket-detail-top">
        <span className="eyebrow"><Plus size={14} /> Новое обращение</span>
        <button type="button" className="icon-button" aria-label="Закрыть форму создания"
          onClick={onCancel} disabled={saving}><X size={18} /></button>
      </div>
      <h2 className="ticket-title">Создать обращение</h2>
      <p className="ticket-create-intro">Выберите автора и опишите, с чем нужна помощь.</p>
      <label htmlFor="new-ticket-user">Автор обращения</label>
      <select id="new-ticket-user" name="userId" value={draft.userId} onChange={changeField}
        required disabled={saving || usersLoading || Boolean(usersError)}>
        <option value="" disabled>{usersLoading ? 'Загружаем пользователей…' : 'Выберите пользователя'}</option>
        {users.map((user) => <option key={user.id} value={String(user.id)}>
          {user.fullName || user.username || user.id} · {user.id}
        </option>)}
      </select>
      {usersError && <div className="ticket-error" role="alert">
        <AlertCircle size={17} /><span>{usersError}</span>
        <button type="button" className="icon-button" aria-label="Повторить загрузку пользователей"
          onClick={() => setUsersVersion((current) => current + 1)} disabled={saving}><RefreshCw size={16} /></button>
      </div>}
      {!usersLoading && !usersError && users.length === 0 && <p className="ticket-muted">Нет доступных пользователей для создания обращения.</p>}
      <label htmlFor="new-ticket-title">Тема</label>
      <input id="new-ticket-title" name="title" value={draft.title} onChange={changeField}
        placeholder="Например, заказ доставили холодным" required disabled={saving} />
      <label htmlFor="new-ticket-description">Описание</label>
      <textarea id="new-ticket-description" name="description" value={draft.description}
        onChange={changeField} placeholder="Расскажите, что произошло" rows={5} required disabled={saving} />
      <fieldset className="ticket-tag-options" disabled={saving || tagsLoading || Boolean(tagsError)}>
        <legend>Метки</legend>
        {tagsLoading ? <span className="ticket-muted" role="status">Загружаем метки…</span> : tags.map((tag) => (
          <label key={tag}>
            <input type="checkbox" name="tags" value={tag} checked={draft.tags.includes(tag)}
              onChange={(event) => {
                const checked = event.target.checked
                setDraft((current) => ({ ...current, tags: checked
                  ? [...current.tags, tag] : current.tags.filter((value) => value !== tag) }))
              }} />
            <span>{tag}</span>
          </label>
        ))}
      </fieldset>
      {tagsError && <div className="ticket-error" role="alert">
        <AlertCircle size={17} /><span>{tagsError}</span>
        <button type="button" className="icon-button" aria-label="Повторить загрузку меток"
          onClick={onRetryTags} disabled={saving}><RefreshCw size={16} /></button>
      </div>}
      {error && <div className="ticket-error" role="alert"><AlertCircle size={17} /><span>{error}</span></div>}
      {!authorized && <p className="auth-hint"><ShieldCheck size={14} /> Войдите как оператор, чтобы создать обращение.</p>}
      <div className="ticket-create-actions">
        <button className="button primary" disabled={!canSubmit}>
          {saving ? <LoaderCircle size={16} className="spin" /> : <Plus size={16} />}
          {saving ? 'Сохраняем…' : 'Создать обращение'}
        </button>
        <button type="button" className="button ticket-reset" onClick={onCancel} disabled={saving}>Отмена</button>
      </div>
    </form>
  )
}
