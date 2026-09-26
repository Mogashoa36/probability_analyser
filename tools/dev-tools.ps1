<#
  Small helper for run-backend.cmd, kept in a file so the batch script doesn't
  have to fight cmd's quoting of > | & ( ) characters.

  Usage:
    powershell -File dev-tools.ps1 Stale      -Jar <jar> -SourceRoot <backend dir>
    powershell -File dev-tools.ps1 FindMaven
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Stale', 'FindMaven')]
    [string]$Action,

    [string]$Jar = '',
    [string]$SourceRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'SilentlyContinue'

switch ($Action) {

    'Stale' {
        if (-not (Test-Path -LiteralPath $Jar)) {
            'STALE'
            break
        }

        $jarTime = (Get-Item -LiteralPath $Jar).LastWriteTime

        # Only files that actually change the build output: Java sources, the
        # build file, and SQL/config resources. "target" is excluded so the jar
        # we are comparing against never marks itself as newer.
        $relevant = @(Get-ChildItem -Path $SourceRoot -Recurse -File |
            Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' } |
            Where-Object { $_.Extension -in '.java', '.sql', '.properties', '.yml', '.yaml' -or $_.Name -eq 'pom.xml' })

        if (-not $relevant) {
            'FRESH'
            break
        }

        $newest = ($relevant | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime

        if ($newest -and $newest -gt $jarTime) { 'STALE' } else { 'FRESH' }
    }

    'FindMaven' {
        # Maven on PATH first, then any Maven already unpacked by a previous
        # Maven-wrapper run (the wrapper caches distributions in ~/.m2/wrapper).
        $onPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($onPath) {
            $onPath.Source
            break
        }

        $dists = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
        if (Test-Path -LiteralPath $dists) {
            $found = Get-ChildItem -Path $dists -Recurse -File -Filter 'mvn.cmd' |
                Sort-Object FullName -Descending |
                Select-Object -First 1
            if ($found) { $found.FullName }
        }
    }
}
