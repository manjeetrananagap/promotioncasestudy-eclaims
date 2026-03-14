// src/components/common/Layout.tsx
import React, { ReactNode, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  FileText, Home, PlusCircle, Users, ClipboardCheck,
  Wrench, LogOut, Bell, Menu, X, ChevronDown
} from 'lucide-react';

const NAV_ITEMS: Record<string, { label: string; href: string; icon: ReactNode }[]> = {
  CUSTOMER: [
    { label: 'Dashboard',    href: '/',           icon: <Home size={18} /> },
    { label: 'My Claims',    href: '/claims',     icon: <FileText size={18} /> },
    { label: 'Submit Claim', href: '/claims/new', icon: <PlusCircle size={18} /> },
  ],
  ADJUSTOR: [
    { label: 'Dashboard',    href: '/',       icon: <Home size={18} /> },
    { label: 'Claims Queue', href: '/claims', icon: <ClipboardCheck size={18} /> },
  ],
  CASE_MANAGER: [
    { label: 'Dashboard',    href: '/',       icon: <Home size={18} /> },
    { label: 'All Claims',   href: '/claims', icon: <Users size={18} /> },
  ],
  SURVEYOR: [
    { label: 'Dashboard',    href: '/',      icon: <Home size={18} /> },
  ],
  PARTNER: [
    { label: 'Dashboard',    href: '/',              icon: <Home size={18} /> },
    { label: 'Work Orders',  href: '/work-orders',   icon: <Wrench size={18} /> },
  ],
};

export default function Layout({ children }: { children: ReactNode }) {
  const { user, roles, isCustomer, isSurveyor, isAdjustor, isCaseManager, logout } = useAuth();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  const role = isCustomer ? 'CUSTOMER'
    : isAdjustor ? 'ADJUSTOR'
    : isCaseManager ? 'CASE_MANAGER'
    : isSurveyor ? 'SURVEYOR'
    : 'PARTNER';

  const navItems = NAV_ITEMS[role] || NAV_ITEMS.PARTNER;
  const roleLabel = role.replace('_', ' ');
  const roleColor = {
    CUSTOMER: 'bg-blue-100 text-blue-800',
    ADJUSTOR: 'bg-purple-100 text-purple-800',
    CASE_MANAGER: 'bg-orange-100 text-orange-800',
    SURVEYOR: 'bg-green-100 text-green-800',
    PARTNER: 'bg-teal-100 text-teal-800',
  }[role];

  return (
    <div className="min-h-screen bg-gray-50 flex">
      {/* Sidebar */}
      <aside className={`
        fixed inset-y-0 left-0 z-50 w-64 bg-[#1A2332] transform transition-transform duration-200
        ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}
        lg:relative lg:translate-x-0 lg:flex lg:flex-col
      `}>
        {/* Logo */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-gray-700">
          <div>
            <span className="text-[#EA4335] font-bold text-xl">nagarro</span>
            <div className="text-gray-400 text-xs">eClaims Platform</div>
          </div>
          <button onClick={() => setMobileOpen(false)} className="lg:hidden text-gray-400">
            <X size={20} />
          </button>
        </div>

        {/* Role badge */}
        <div className="px-6 py-3">
          <span className={`text-xs font-semibold px-2 py-1 rounded-full ${roleColor}`}>
            {roleLabel}
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-4 py-2 space-y-1">
          {navItems.map(({ label, href, icon }) => {
            const active = location.pathname === href;
            return (
              <Link
                key={href}
                to={href}
                onClick={() => setMobileOpen(false)}
                className={`
                  flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors
                  ${active
                    ? 'bg-[#1A73E8] text-white'
                    : 'text-gray-300 hover:bg-gray-700 hover:text-white'}
                `}
              >
                {icon}
                {label}
              </Link>
            );
          })}
        </nav>

        {/* User info bottom */}
        <div className="px-4 py-4 border-t border-gray-700">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-[#1A73E8] flex items-center justify-center text-white font-bold text-sm">
              {user?.name?.charAt(0).toUpperCase() || 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-white text-sm font-medium truncate">{user?.name}</div>
              <div className="text-gray-400 text-xs truncate">{user?.email}</div>
            </div>
          </div>
          <button
            onClick={logout}
            className="mt-3 w-full flex items-center gap-2 text-gray-400 hover:text-white text-sm px-2 py-1.5 rounded hover:bg-gray-700 transition-colors"
          >
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar */}
        <header className="bg-white border-b border-gray-200 px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => setMobileOpen(true)} className="lg:hidden text-gray-500">
              <Menu size={22} />
            </button>
            <h1 className="text-gray-800 font-semibold text-lg">eClaims</h1>
          </div>
          <div className="flex items-center gap-3">
            <button className="relative text-gray-500 hover:text-gray-800">
              <Bell size={20} />
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 rounded-full text-white text-xs flex items-center justify-center">
                3
              </span>
            </button>
            <button
              onClick={() => setUserMenuOpen(v => !v)}
              className="flex items-center gap-2 text-sm text-gray-700 hover:text-gray-900"
            >
              <div className="w-8 h-8 rounded-full bg-[#1A73E8] flex items-center justify-center text-white font-bold text-xs">
                {user?.name?.charAt(0).toUpperCase()}
              </div>
              <ChevronDown size={14} />
            </button>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-auto p-6">
          {children}
        </main>
      </div>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}
    </div>
  );
}
