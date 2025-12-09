// React Imports
import type { SVGAttributes } from 'react'

const Logo = (props: SVGAttributes<SVGElement>) => {
  return (
    

    <svg
  version="1.1"
  xmlns="http://www.w3.org/2000/svg"
  width="65"
  height="54"    
  viewBox="0 0 140 140"
  preserveAspectRatio="xMidYMid meet"
>
  <defs>
    <radialGradient id="outerGradient" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#0d9ad1"/>
      <stop offset="100%" stop-color="#005a88"/>
    </radialGradient>
  <radialGradient id="middleGradient" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#74d5e8"/>
      <stop offset="100%" stop-color="#00838f"/>
    </radialGradient>

    <radialGradient id="centerGradient" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#ffe95a"/>
      <stop offset="100%" stop-color="#ff9800"/>
    </radialGradient>

    <filter id="lineShadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="1.5" stdDeviation="2" flood-color="#000000" flood-opacity="0.25"/>
    </filter>
  </defs>

  <rect width="140" height="140" fill="none"/>

  <circle cx="70" cy="70" r="65" fill="url(#outerGradient)" />

  <circle cx="70" cy="70" r="48" fill="url(#middleGradient)" opacity="0.9"/>

  <circle cx="70" cy="70" r="35" fill="url(#middleGradient)" opacity="0.9"/>

  <circle cx="70" cy="70" r="22" fill="url(#centerGradient)" />

  <g filter="url(#lineShadow)">
    <path
      d="
        M10 68
        H45
        L52 56
        L59 78
        L67 28
        L73 104
        L82 54
        L90 68
        H130
        L130 76
        H90
        L82 62
        L73 112
        L67 36
        L59 86
        L52 64
        L45 76
        H10
        Z
      "
      fill="#ffffff"
    />
  </g>
</svg>

      )
}

export default Logo
