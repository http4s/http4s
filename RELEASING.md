# Release procedure

## v0.23.xx releases

1. Check if there's a milestone for the release. Are we in a good state for the release? Are there any outstanding PRs that could be merged or should be waited for?
1. Press the "Draft a new release" button in GitHub.
1. Create a new tag with the appropriate name (e.g. v0.23.28); also name the release in GitHub accordingly.
1. Make sure you're targeting the `series/0.23` branch.
1. Press the "Generate new release notes" button in GitHub.
1. Manually edit the generated release notes:
    - Review the auto-labeling and categorization of PRs.
    - Put "Behind the scenes" section in to a `details` block.
    - Move any interesting or important behind the scenes PRs into the relevant categories. In particular, any bumps to public dependencies should be moved to the section for the relevant http4s module.
    - Just make it look nice :)
1. Save the release as a draft.
1. Copy the edited release notes into `docs/changelog.md`, at the top of the file.
    - Follow the same header style: "v0.23.xx (YYYY-MM-DD)".
1. Create a branch and commit the changes to the changelog. Open a PR, and wait for tests to pass and for someone to approve.
1. Merge the changelog PR.
1. Now you're ready to release! Edit your draft release on GitHub and press the green "Publish Release" button.
1. Wait for CI and publishing to finish, and then announce the release (mastodon, discord).
1. Merge `series/0.23` to `main` and open a PR.
    - There will likely be conflicts here that need working through. Don't be afraid to ask for help.

## v1.0.0-Mxx releases

1. Get reviews and merge the PR from the last v0.23.xx step (to merge any `series/0.23` changes to `main`)
1. Check if there's a milestone for the release. Are we in a good state for the milestone release? Any outstanding PRs that could be merged or should be waited for?
1. Press the "Draft a new release" button in GitHub.
1. Create a new tag with the appropriate name (eg. v1.0.0-M43); also name the release in GitHub accordingly.
1. Make sure you're targeting the `main` branch.
1. Press the "Generate new release notes" button in GitHub.
1. Manually edit the generated release notes:
    - Review the auto-labeling and categorization of PRs.
    - Put "Behind the scenes" section in to a `details` block.
    - Move any interesting or important behind the scenes PRs into the relevant categories. In particular, any bumps to public dependencies should be moved to the section for the relevant http4s module.
    - Just make it look nice :)
1. Save the release as a draft.
1. Copy the edited release notes into `docs/changelog.md`, at the top of the file.
    - Follow the same header style: "v1.0.0-Mxx (YYYY-MM-DD)"
1. Create a branch and commit the changes to the changelog. Open a PR, and wait for tests to pass and for someone to approve.
1. Merge the changelog PR.
1. Now you're ready to release! Edit you draft release on GitHub.
    - click the checkbox for `Set as a pre-release`.
    - make sure that `Set as the latest release` is NOT CHECKED.
    - click the checkbox for `Create a discussion for this release`. Set the category for the discussion to `Releases`.
1. Press the green "Publish Release" button.
    - Edit the resulting discussion to add the "Ancillary repo releases" section
      <details><summary>Ancillary repo releases copy paste</summary>

      ```
      ---

      ## Ancillary repo releases:

      Modules where a volunteer maintainer steps up cut to the front of the line! :smile:

      * [ ] armeria
      * [ ] blaze
      * [ ] boopickle
      * [ ] dom
      * [ ] fabric
      * [ ] feral
      * [ ] finagle
      * [ ] fs2-data
      * [ ] jdk-http-client
      * [ ] jetty
      * [ ] netty
      * [ ] prometheus-client
      * [ ] rho
      * [ ] scala-xml
      * [ ] scalatags
      * [ ] servlet
      * [ ] session
      * [ ] twirl
      ```
      </details>
1. Wait for CI and publishing to finish!
