<template>
  <header class="navbar">
    <div class="navbar-inner">
      <router-link to="/" class="logo">My App</router-link>
      <nav class="nav-links">
        <router-link to="/">首页</router-link>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  background: #1a1a2e;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.navbar-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  font-size: 1.25rem;
  font-weight: 600;
  color: #fff;
  text-decoration: none;
}

.nav-links {
  display: flex;
  gap: 24px;
}

.nav-links a {
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  font-size: 0.95rem;
  transition: color 0.2s;
}

.nav-links a:hover,
.nav-links a.router-link-active {
  color: #fff;
}

@media (max-width: 768px) {
  .navbar-inner {
    height: 48px;
  }

  .logo {
    font-size: 1.1rem;
  }

  .nav-links {
    gap: 16px;
  }
}
</style>
