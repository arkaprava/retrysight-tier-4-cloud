{{/*
RetrySight Helm helpers
*/}}
{{- define "retrysight.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "retrysight.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "retrysight.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "retrysight.labels" -}}
helm.sh/chart: {{ include "retrysight.chart" . }}
{{ include "retrysight.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: retrysight
retrysight.io/tier: {{ .Values.tier.slug }}
{{- end }}

{{- define "retrysight.selectorLabels" -}}
app.kubernetes.io/name: {{ include "retrysight.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "retrysight.image" -}}
{{- $registry := .Values.images.registry -}}
{{- $repo := .Values.images.repository -}}
{{- $tag := .Values.images.tag | default .Chart.AppVersion -}}
{{- if $repo -}}
{{- printf "%s/%s:%s" $registry $repo $tag -}}
{{- else -}}
{{- printf "%s:%s" .image $tag -}}
{{- end -}}
{{- end }}

{{- define "retrysight.componentImage" -}}
{{- $registry := .root.Values.images.registry -}}
{{- $repo := .root.Values.images.repository -}}
{{- $tag := .root.Values.images.tag | default .root.Chart.AppVersion -}}
{{- if .root.Values.images.composeStyle -}}
{{- /* CDN / docker-compose tags: retrysight/tier-core-manager:VERSION */ -}}
{{- printf "%s-%s:%s" $repo .component $tag -}}
{{- else if $registry -}}
{{- printf "%s/%s/%s:%s" $registry $repo .component $tag -}}
{{- else -}}
{{- printf "%s/%s:%s" $repo .component $tag -}}
{{- end -}}
{{- end }}

{{/*
Secret value: explicit value, else the value already stored in this release's
Secret (keeps upgrades stable), else a random string.
Usage: include "retrysight.secretValue" (list $ "jwt-secret" .Values.manager.jwtSecret 48)
*/}}
{{- define "retrysight.secretValue" -}}
{{- $ctx := index . 0 -}}
{{- $key := index . 1 -}}
{{- $provided := index . 2 -}}
{{- $len := index . 3 -}}
{{- if $provided -}}
{{- $provided -}}
{{- else -}}
{{- $existing := lookup "v1" "Secret" $ctx.Release.Namespace (printf "%s-secrets" (include "retrysight.fullname" $ctx)) -}}
{{- $data := ($existing.data | default dict) -}}
{{- if hasKey $data $key -}}
{{- index $data $key | b64dec -}}
{{- else -}}
{{- randAlphaNum $len -}}
{{- end -}}
{{- end -}}
{{- end }}
