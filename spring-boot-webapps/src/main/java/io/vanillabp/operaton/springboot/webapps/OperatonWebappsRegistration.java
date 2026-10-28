package io.vanillabp.operaton.springboot.webapps;

import static org.operaton.bpm.engine.authorization.Authorization.ANY;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.engine.authorization.Permissions.ALL;

import java.util.ArrayList;
import java.util.List;

import org.operaton.bpm.container.RuntimeContainerDelegate;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.authorization.Groups;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;
import io.vanillabp.operaton.springboot.webapps.OperatonWebappsProperties.AdminUser;

/**
 * Makes the engines VanillaBP built visible to Cockpit, Tasklist and Admin.
 *
 * <p>
 * The webapps do not look for {@code ProcessEngine} beans. They ask the
 * {@link RuntimeContainerDelegate} instead. VanillaBP does not register its engines there,
 * because nothing in the adapter needs a global registry. So this class registers every
 * engine whose adapter id has the webapps enabled. It removes the engine again when the
 * context stops, so a second context in the same JVM does not find a stale engine.
 * </p>
 *
 * <p>
 * The engine name is part of the webapps' URLs
 * (<code>/operaton/app/cockpit/vanillabp-operaton-&lt;adapter-id&gt;/</code>).
 * <code>/operaton</code> redirects to it. With several adapter ids the webapps offer all
 * of them in their engine switcher.
 * </p>
 */
public class OperatonWebappsRegistration implements SmartLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(OperatonWebappsRegistration.class);

  private final ObjectProvider<OperatonEngineHolder> engines;

  private final OperatonWebappsProperties properties;

  private final List<ProcessEngine> registered = new ArrayList<>();

  private boolean running;

  /**
   * Remembers what it registered. So the webapps are left as they were found when the
   * application shuts down.
   *
   * @param engines The engines VanillaBP built, resolved lazily because they come into
   *          existence while the context refreshes
   * @param properties Which adapter ids the webapps serve, and who the administrator is
   */
  public OperatonWebappsRegistration(
      final ObjectProvider<OperatonEngineHolder> engines,
      final OperatonWebappsProperties properties) {

    this.engines = engines;
    this.properties = properties;

  }

  @Override
  public void start() {

    engines
        .orderedStream()
        .forEach(holder -> {

          final var webapps = properties.webappsOf(holder.getAdapterId());
          if (!webapps.isEnabled()) {

            logger.debug(
                "Operaton[{}]: the webapps are disabled for this adapter id",
                holder.getAdapterId());
            return;

          }

          final var engine = holder.getProcessEngine();
          final var container = RuntimeContainerDelegate.INSTANCE.get();
          if (container.getProcessEngineService().getProcessEngine(engine.getName()) == null) {
            container.registerProcessEngine(engine);
            registered.add(engine);
          }

          logger.info(
              "Operaton[{}]: Cockpit, Tasklist and Admin serve the engine '{}'",
              holder.getAdapterId(),
              engine.getName());

          createAdminUser(holder.getAdapterId(), engine, webapps.getAdminUser());

        });

    running = true;

  }

  @Override
  public void stop() {

    final var container = RuntimeContainerDelegate.INSTANCE.get();
    registered.forEach(container::unregisterProcessEngine);
    registered.clear();
    running = false;

  }

  @Override
  public boolean isRunning() {

    return running;

  }

  @Override
  public int getPhase() {

    // after the engines were built and the workflow processing started
    return Integer.MAX_VALUE - 1;

  }

  /**
   * Creates the configured administrator, the group <code>operaton-admin</code> and the
   * grants that group needs. The webapps expect to find all three. Without a member of that
   * group they open their setup wizard instead of the login. The recipe is Operaton's own,
   * see their {@code CreateAdminUserConfiguration}.
   *
   * @param adapterId The adapter id whose engine is set up
   * @param engine    The engine
   * @param adminUser The configured administrator, or <code>null</code>
   */
  private void createAdminUser(
      final String adapterId,
      final ProcessEngine engine,
      final AdminUser adminUser) {

    if (adminUser == null) {
      return;
    }

    validate(adapterId, adminUser);

    final var identityService = engine.getIdentityService();
    if (identityService.createUserQuery().userId(adminUser.getId()).singleResult() != null) {

      logger.debug(
          "Operaton[{}]: the user '{}' exists already",
          adapterId,
          adminUser.getId());
      return;

    }

    final var user = identityService.newUser(adminUser.getId());
    user.setPassword(adminUser.getPassword());
    user.setFirstName(adminUser.getFirstName());
    user.setLastName(adminUser.getLastName());
    user.setEmail(adminUser.getEmail());
    identityService.saveUser(user);

    createAdminGroup(identityService);
    grantAdminPermissions(engine);
    identityService.createMembership(adminUser.getId(), org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN);

    logger.info(
        """
            Operaton[{}]: created the user '{}' as a member of '{}', which grants every \
            permission on everything this engine knows. It exists because \
            'vanillabp.adapters.{}.webapps.admin-user' says so - an application which \
            brings its own identity provider leaves that section out.""",
        adapterId,
        adminUser.getId(),
        org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN,
        adapterId);

  }

  private void validate(
      final String adapterId,
      final AdminUser adminUser) {

    if ((adminUser.getId() == null) || adminUser.getId().isBlank()) {

      throw new IllegalStateException(
          """
              The Operaton adapter '%s' is asked to create an administrator for the \
              webapps, but 'vanillabp.adapters.%s.webapps.admin-user.id' is missing. Add \
              the user id, or remove the entire 'admin-user' section to have the webapps \
              show their setup wizard instead."""
              .formatted(adapterId, adapterId));

    }

    if ((adminUser.getPassword() == null) || adminUser.getPassword().isBlank()) {

      throw new IllegalStateException(
          """
              The Operaton adapter '%s' is asked to create the administrator '%s' for the \
              webapps, but 'vanillabp.adapters.%s.webapps.admin-user.password' is missing. \
              Add the password, or remove the entire 'admin-user' section to have the \
              webapps show their setup wizard instead."""
              .formatted(adapterId, adminUser.getId(), adapterId));

    }

  }

  private void createAdminGroup(
      final IdentityService identityService) {

    if (identityService.createGroupQuery().groupId(org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN)
        .count() > 0) {
      return;
    }

    final var group = identityService.newGroup(org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN);
    group.setName("Operaton BPM Administrators");
    group.setType(Groups.GROUP_TYPE_SYSTEM);
    identityService.saveGroup(group);

  }

  private void grantAdminPermissions(
      final ProcessEngine engine) {

    final var authorizationService = engine.getAuthorizationService();

    for (final var resource : Resources.values()) {

      final var exists = authorizationService
          .createAuthorizationQuery()
          .groupIdIn(org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN)
          .resourceType(resource)
          .resourceId(ANY)
          .count() > 0;
      if (exists) {
        continue;
      }

      final var grant = new AuthorizationEntity(AUTH_TYPE_GRANT);
      grant.setGroupId(org.operaton.bpm.engine.authorization.Groups.OPERATON_ADMIN);
      grant.setResource(resource);
      grant.setResourceId(ANY);
      grant.addPermission(ALL);
      authorizationService.saveAuthorization(grant);

    }

  }

}
