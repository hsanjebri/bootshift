/** Same rules as the backend's RepositoryName: GitHub owner (39 chars) / name (100 chars). */
export const REPOSITORY_PATTERN = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]{1,100}$/;

/** Same rule as the backend's StartRunRequest.BRANCH: no "..", no leading '-' or '/'. */
export const BRANCH_PATTERN = /^(?!.*\.\.)(?![-/])[A-Za-z0-9._/-]{1,255}$/;
