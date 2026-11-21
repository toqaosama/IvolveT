# Lab 27: Securing Sensitive Data with Ansible Vault

## Objective

1. Create a `mysql` role that:
   - installs MySQL
   - creates the `ivolve` database
   - creates `ivolveuser` with ALL privileges on the `ivolve` database
2. Encrypt the database user password with **Ansible Vault**.
3. Validate on the managed node: connect with the new user and list databases.

> **How it was run:** the control node is a Linux container (`ansible-control`, Python 3.12 +
> `pip install ansible-core`) because `sudo` on the WSL machine needs a password. Any Linux
> control node works the same way. Managed node: AWS EC2 Ubuntu 26.04 (`75.101.215.238`).

## Structure

```text
lab-27/
├── ansible.cfg
├── inventory.ini
├── mysql.yml                       # playbook that runs the role
├── vault.yml.example               # template of the secret file (not used directly)
├── group_vars/managed/vault.yml    # ENCRYPTED - created with ansible-vault
└── roles/mysql/
    ├── defaults/main.yml           # db name, user; password = vault_mysql_user_password
    ├── handlers/main.yml
    ├── meta/main.yml
    └── tasks/main.yml
```

## How the secret flows

```text
group_vars/managed/vault.yml  (AES256 encrypted)
    vault_mysql_user_password: ********
            │
            ▼
roles/mysql/defaults/main.yml
    mysql_user_password: "{{ vault_mysql_user_password }}"
            │
            ▼
community.mysql.mysql_user  (no_log: true → password never printed)
```

## Steps

### 1. Prepare

```bash
cp -r /mnt/f/nti/ivolve/IvolveT/lab-27 ~/ && cd ~/lab-27
# put the managed node IP in inventory.ini
ansible-galaxy collection install ansible.mysql   # (formerly community.mysql)
```

### 2. Create the encrypted vault file

```bash
ansible-vault create group_vars/managed/vault.yml
```

Content:

```yaml
vault_mysql_user_password: "YourStrongPassword123"
```

Check that it is encrypted:

```bash
cat group_vars/managed/vault.yml          # $ANSIBLE_VAULT;1.1;AES256 ...
ansible-vault view group_vars/managed/vault.yml
```

![Vault file is encrypted](screenshots/vault-encrypted.png)

Other vault commands: `ansible-vault edit`, `ansible-vault rekey`, `ansible-vault encrypt_string`.

### 3. Run the playbook

```bash
ansible-playbook mysql.yml --ask-vault-pass
```

or with a password file (kept out of Git by `.gitignore`):

```bash
echo 'my-vault-pass' > .vault_pass && chmod 600 .vault_pass
ansible-playbook mysql.yml --vault-password-file .vault_pass
```

Result: `ok=8 failed=0`:

![Playbook run](screenshots/playbook-run.png)

> MySQL 8.4+ (Ubuntu 26.04) disables `mysql_native_password`, so the role creates the user with
> `plugin: caching_sha2_password` + `plugin_auth_string`. With plain `password:` the task fails with
> `Plugin 'mysql_native_password' is not loaded`.

The last tasks log in as `ivolveuser`, run `SHOW DATABASES;` and assert that `ivolve` is listed.

### 4. Validate manually on the managed node

```bash
ssh -i ~/.ssh/ansible_key ubuntu@<MANAGED_NODE_IP>
mysql -u ivolveuser -p -e "SHOW DATABASES;"
mysql -u ivolveuser -p -e "SHOW GRANTS;"
```

![SHOW DATABASES and GRANTS as ivolveuser](screenshots/show-databases.png)

Expected:

```text
+--------------------+
| Database           |
+--------------------+
| information_schema |
| ivolve             |
| performance_schema |
+--------------------+
```

## Screenshots

| Step | Screenshot |
|---|---|
| Encrypted vault file | [vault-encrypted.png](screenshots/vault-encrypted.png) |
| Playbook run | [playbook-run.png](screenshots/playbook-run.png) |
| Validate DB as ivolveuser | [show-databases.png](screenshots/show-databases.png) |
