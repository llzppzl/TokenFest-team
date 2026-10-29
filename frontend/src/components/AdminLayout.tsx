import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../authContext'

const links = [
  { to: '/admin/overview', label: 'Overview' },
  { to: '/admin/requests', label: 'Requests' },
  { to: '/admin/providers', label: 'Providers' },
  { to: '/admin/keys', label: 'Keys' },
  { to: '/admin/settings', label: 'Settings' },
]

export default function AdminLayout() {
  const { session, logout } = useAuth()
  return (
    <div className="flex min-h-screen flex-col bg-gray-50 md:flex-row">
      <aside className="flex flex-col border-b border-gray-200 bg-white md:w-56 md:border-b-0 md:border-r">
        <div className="px-5 py-4 text-xl font-bold">TokenFest</div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-1 md:flex-col md:pb-0">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium ${
                  isActive ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden border-t px-5 py-4 text-sm md:block">
          <div className="text-gray-500">Signed in as</div>
          <div className="font-medium">{session?.name}</div>
          <button onClick={logout} className="mt-2 text-indigo-600 hover:underline">
            Log out
          </button>
        </div>
      </aside>
      <main className="flex-1 p-4 md:p-8">
        <Outlet />
        <button onClick={logout} className="mt-8 text-sm text-indigo-600 hover:underline md:hidden">
          Log out
        </button>
      </main>
    </div>
  )
}
